import com.atlassian.jira.bc.issue.IssueService
import com.atlassian.jira.event.type.EventDispatchOption
import com.atlassian.jira.issue.IssueInputParametersImpl
import com.atlassian.jira.issue.IssueManager
import com.atlassian.jira.issue.MutableIssue
import com.atlassian.jira.issue.UpdateIssueRequest
//import com.mysema.query.sql.types.Null
import com.opensymphony.workflow.loader.ActionDescriptor
import com.opensymphony.workflow.loader.StepDescriptor
import org.apache.log4j.Logger
import org.apache.log4j.Level
import com.atlassian.jira.component.ComponentAccessor
import com.atlassian.jira.issue.comments.CommentManager
import com.atlassian.jira.issue.comments.Comment
import com.onresolve.scriptrunner.canned.jira.workflow.postfunctions.SendCustomEmail

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

import java.util.Calendar;
import java.util.GregorianCalendar;

import com.atlassian.jira.issue.CustomFieldManager

import com.atlassian.jira.component.ComponentAccessor
import com.atlassian.jira.issue.Issue
import com.atlassian.jira.issue.IssueManager
import com.atlassian.jira.user.ApplicationUser


log = Logger.getLogger("com.acme.CreateSubtask")
log.setLevel(Level.DEBUG)
issueManager = ComponentAccessor.getIssueManager()


def send_email(text) {
    def debug_issue = issueManager.getIssueObject("SCR-1531")
    def subject = debug_issue.getKey() + " " + debug_issue.getSummary()
    ArrayList recievers = new ArrayList()
    recievers.add("v.monakhov@dssl.ru")
    recievers.add("v.agafonov@dssl.ru")
    recievers.add("p.shwarts@dssl.ru")
    def params = [
            "issue"                                       : debug_issue,
            (SendCustomEmail.FIELD_EMAIL_TEMPLATE)        : text,
            (SendCustomEmail.FIELD_EMAIL_SUBJECT_TEMPLATE): subject,
            (SendCustomEmail.FIELD_TO_ADDRESSES)          : String.join(",", recievers),
            (SendCustomEmail.FIELD_EMAIL_FORMAT)          : "HTML"
    ]

    def sendCustomEmail = new SendCustomEmail()
    if (!sendCustomEmail.doValidate(params, false).hasAnyErrors()) {
        sendCustomEmail.doScript(params)
        return true
    } else {
        err_string = "{color:#FF0000}SEND MAIL ERROR: " + sendCustomEmail.doValidate(params, false).errors + "{color}"
        log.debug err_string
        write_comment_log(err_string)
        return false
    }
}

def write_comment_log(str) {
    CommentManager commentMgr = ComponentAccessor.getCommentManager()
    def debug_issue = issueManager.getIssueObject("SCR-1531")
    def user = ComponentAccessor.getUserManager().getUserByName("v.monakhov")
    def comments = commentMgr.getComments(debug_issue);
    ArrayList to_delete = new ArrayList()
    def max_comments = 200
    def num_to_delete = comments.size() - max_comments
    for (int i=0; i<num_to_delete; i++) {
        to_delete.add(comments[i])
    }
    for (int i=0;i<to_delete.size();i++) {
        commentMgr.delete(to_delete[i])
    }
    commentMgr.create(debug_issue, user, str, false)
    updateIssue(debug_issue, user)
}

def write_log(str) {
    CommentManager commentMgr = ComponentAccessor.getCommentManager()
    def debug_issue = issueManager.getIssueObject("SCR-1531")
    old_text = debug_issue.getDescription()
    debug_issue.setDescription(old_text + str)
    def user = ComponentAccessor.getUserManager().getUserByName("v.monakhov")
    def comments = commentMgr.getComments(debug_issue);
    updateIssue(debug_issue, user)
}

def is_holiday(c_day) {
    DateFormat dateFormat = new SimpleDateFormat("dd.MM.yy");
    def holidays = [
            "08.03.18",
            "09.03.18",
            "10.03.18",
            "11.03.18",
            "29.04.18",
            "30.04.18",
            "01.05.18",
            "02.05.18",
            "09.05.18",
            "10.06.18",
            "11.06.18",
            "12.06.18",
            "03.10.18",
            "04.10.18",
            "05.10.18"
    ]
    for (int i=0; i<holidays.size(); i++) {
        if (holidays[i] == dateFormat.format(c_day.getTime())) {
            return true
        }
    }
    return false
}

def is_no_work(c_day) {
    DateFormat dateFormatTest = new SimpleDateFormat("dd.MM.yyyy HH:mm:SS");

    if (c_day.get(Calendar.DAY_OF_WEEK) == Calendar.SATURDAY){
        return true
    }
    Calendar next_day = new GregorianCalendar();
    next_day = (Calendar) c_day.clone();
    next_day.add(Calendar.DATE, 1)
    if (c_day.get(Calendar.DAY_OF_WEEK) == Calendar.FRIDAY ||
        c_day.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY ||
        (is_holiday(next_day) && !is_holiday(c_day))) {
        if (c_day.get(Calendar.DAY_OF_WEEK) == Calendar.FRIDAY ||
            is_holiday(next_day)) {
            if (c_day.get(Calendar.HOUR_OF_DAY) >= 18 &&
                    (c_day.get(Calendar.MINUTE) > 30 || c_day.get(Calendar.HOUR_OF_DAY) > 18)) {
                return true
            }
        }
        else if (c_day.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY ||
                (!is_holiday(next_day) && is_holiday(c_day))) {
            if (c_day.get(Calendar.HOUR_OF_DAY) <= 18 &&
                    (c_day.get(Calendar.MINUTE) < 30 || c_day.get(Calendar.HOUR_OF_DAY) < 18)) {
                return true
            }
        }
    }
    if (is_holiday(c_day) && is_holiday(next_day)) {
        return true
    }
    return false
}

def has_assign_group(groups) {
    for (int i; i<groups.size(); i++) {
        if (groups[i].getName() == "sd-OnLine"  ||
            groups[i].getName() == "sd-OffLine" ||
            groups[i].getName() == "sd-absent") {
            return true
        }
    }
}

def to_unassigned(user_initiator, user_assigned, c_day) {
    def groups_initiator = ComponentAccessor.getGroupManager().getGroupsForUser(user_initiator)
    def groups_assigned = ComponentAccessor.getGroupManager().getGroupsForUser(user_assigned)

    if (!groups_initiator.size() || user_initiator.getName() == "callcenter") {
        if (is_no_work(c_day)) {
            return true
        } else if (!is_no_work(c_day) && has_assign_group(groups_assigned)) {
            return true
        }
    }
    return false
}

def updateIssue(MutableIssue issue, user) {
    issueManager.updateIssue(user, issue, createIssueUpdateRequest())
}

def createIssueUpdateRequest() {
    new UpdateIssueRequest.UpdateIssueRequestBuilder()
            .eventDispatchOption(EventDispatchOption.DO_NOT_DISPATCH)
            .sendMail(false)
            .build()
}

def run_test(user_init, user_assigned, c_day, expected_res, test_id) {
    DateFormat dateFormatTest = new SimpleDateFormat("dd.MM.yyyy HH:mm:SS");
    def res = to_unassigned(user_init, user_assigned, c_day)
    def str = "\n============= run test " + test_id + " =================\n"
    str += "\nday_int " + c_day.get(Calendar.DAY_OF_WEEK) +
            " date " + dateFormatTest.format(c_day.getTime()) + "\n" +
            "user_init name: " + user_init.getName() + "\n" +
            "user_assigned: " + user_assigned.getName() + "\n" +
            "to unassigned: " + res + "; expected: " + expected_res + "\n"
    if (res == expected_res) {
        str += "Test " + test_id + ": {color:#00FF00}OK{color}\n"
    } else {
        str += "Test " + test_id + ": {color:#FF0000}FAIL{color}\n"
    }
    return str
}

def no_work_test(c_day, expected_res) {
    DateFormat dateFormatTest = new SimpleDateFormat("dd.MM.yyyy HH:mm:SS");
    def res = is_no_work(c_day)
    def str = "day_int " + get_day_string(c_day.get(Calendar.DAY_OF_WEEK)) +
            " date " + dateFormatTest.format(c_day.getTime()) +
            " no work, unassign: " + res + "; status: "
    if (expected_res == res) {
        str += "{color:#00FF00}OK{color}\n"
    } else {
        str += "{color:#FF0000}FAIL{color}\n"
    }
    return str
}

def get_day_string(int_val) {
    Map< Integer, String > map = new HashMap< Integer, String >();
    map.put(1,"SUNDAY");
    map.put(2,"MONDAY");
    map.put(3,"TUESDAY");
    map.put(4,"WEDNESDAY");
    map.put(5,"THURSDAY");
    map.put(6,"FRIDAY");
    map.put(7,"SATURDAY");
    return map.get(int_val);
}

def get_ev_log_text(event, c_day, result, assignee_user) {
    DateFormat dateFormatTest = new SimpleDateFormat("dd.MM.yyyy HH:mm:SS");
    def str = "Initiator: " + event.getUser().getName() + "\n"
    str += "Assignee: " + assignee_user + "\n"
    str += "Issue: " + event.issue + "\n"
    str += "Weekday: " + get_day_string(c_day.get(Calendar.DAY_OF_WEEK)) +
            "; date: " + dateFormatTest.format(c_day.getTime()) + "\n"
    str += "Unassign: " + result + "\n"
    return str
}

def get_open_id_by_issue(MutableIssue issue) {
    def workflow = ComponentAccessor.getWorkflowManager().getWorkflow(issue)

    StepDescriptor oStep = workflow.getLinkedStep(issue.getStatusObject());
    List<ActionDescriptor> oActions = oStep.getActions()
    for(ActionDescriptor oAction : oActions)
    {
        if (oAction.getName() == "Открыть") return oAction.getId()
    }
    return -1
}

def open_issue(issue) {
    def act_id = get_open_id_by_issue(issue)
    if (act_id != -1) {
        IssueService issueService = ComponentAccessor.getIssueService()
        transitionValidationResult = issueService.validateTransition(issue.getAssignee(), issue.id, act_id, new IssueInputParametersImpl())
        transitionResult = issueService.transition(issue.getAssignee(), transitionValidationResult)
        if (transitionResult.isValid()) {
            return true
        }
    }
    return false
}

def main(event) {
    def assignee_user = event.issue.getAssignee()
    def groups_initiator = ComponentAccessor.getGroupManager().getGroupsForUser(event.getUser())
    if ((!groups_initiator.size() || event.getUser().getName() == "callcenter") &&
            event.issue.getProjectObject().getKey() != "SCR") {
        if (event.issue.getStatusObject().getName() == "Open") {
            log.debug "initiator: " + event.getUser().getName() +
                    "; project: " + event.issue.getProjectObject().getKey() +
                    "; already opened, break"
            return
        }
        else {
            def open_res = open_issue(event.issue)
            if (!open_res) {
                throw new IllegalArgumentException ("Incorrect open transition res, issue (" + event.issue + ") " +
                        event.issue.getKey() + " " + event.issue.getSummary() +
                        " from transition " + event.issue.getStatusObject().getName())
            }
        }
        Calendar c_day = new GregorianCalendar();
        def res = to_unassigned(event.getUser(), event.issue.getAssignee(), c_day)
        if (res) {
            event.issue.setAssigneeId(null)
        }
        def str_log = get_ev_log_text(event, c_day, res, assignee_user)
        write_comment_log(str_log)
    } else {
        log.debug "initiator: " + event.getUser().getName() +
                "; groups_size: " + groups_initiator.size() +
                "; project: " + event.issue.getProjectObject().getKey() + "; break"
    }
    def update_user = ComponentAccessor.getUserManager().getUserByName("v.agafonov")
    try{
        updateIssue(event.issue, update_user)
    } catch (groovy.lang.MissingMethodException e) {}
}

/*
def test_issue = issueManager.getIssueObject("SM-77")
test_user = ComponentAccessor.getUserManager().getUserByName("v.agafonov")
open_issue(test_issue)
test_issue.setAssigneeId(null)
try{
    updateIssue(test_issue, test_user)
} catch (groovy.lang.MissingMethodException e) {}
*/

/*
try {
    main()
} catch (Exception e) {
    def script_name = "comment_reopen" + this.class.getName()
    def ex_string = "ERROR in script " + script_name + ": " +  e +
                    "; issue: " + event.issue
    send_email("<font color=\"red\">" + ex_string + "</font>")
    write_comment_log("{color:#FF0000}" + ex_string + "{color}")
}
*/
//==============================TESTS==================================//

class TestEvent {
    Issue issue;
    ApplicationUser initiator;

    ApplicationUser getUser() {
        return initiator;
    }
}

def test_event() {
    TestEvent test_ev = new TestEvent();
    IssueManager issueManager = ComponentAccessor.getIssueManager()
    Issue test_issue = issueManager.getIssueObject("SM-77")

    test_ev.issue = test_issue;
    test_ev.initiator = ComponentAccessor.getUserManager().getUserByName("v.agafonov")

    main()
}

test_event()

def user_tests() {
    def debug_issue = issueManager.getIssueObject("SCR-1531")
    debug_issue.setDescription("")
    def debug_user = ComponentAccessor.getUserManager().getUserByName("v.monakhov")
    updateIssue(debug_issue, debug_user)

    Calendar wednesday_usual_day = new GregorianCalendar();
    wednesday_usual_day.set(2018, 3, 2, 14, 5, 20);
    wednesday_usual_day.setWeekDate(2018, 9, 4);
    def str_res = ""

    def user_init1 = ComponentAccessor.getUserManager().getUserByName("callcenter")
    def user_assigned1 = ComponentAccessor.getUserManager().getUserByName("v.drynov")
    str_res += run_test(user_init1, user_assigned1, wednesday_usual_day, true, 1)

    def user_init2 = ComponentAccessor.getUserManager().getUserByName("a.shifmakher")
    def user_assigned2 = ComponentAccessor.getUserManager().getUserByName("v.drynov")
    str_res += run_test(user_init2, user_assigned2, wednesday_usual_day, false, 2)

    def user_init3 = ComponentAccessor.getUserManager().getUserByName("0095482@gmail.com")
    def user_assigned3 = ComponentAccessor.getUserManager().getUserByName("v.drynov")
    str_res += run_test(user_init3, user_assigned3, wednesday_usual_day, true, 3)

    def user_init4 = ComponentAccessor.getUserManager().getUserByName("0095482@gmail.com")
    def user_assigned4 = ComponentAccessor.getUserManager().getUserByName("a.kurnosikov")
    str_res += run_test(user_init4, user_assigned4, wednesday_usual_day, false, 4)

    def user_init5 = ComponentAccessor.getUserManager().getUserByName("callcenter")
    def user_assigned5 = ComponentAccessor.getUserManager().getUserByName("s-vasiliev")
    str_res += run_test(user_init5, user_assigned5, wednesday_usual_day, false, 5)

    write_log(str_res)
    return str_res
}

def no_work_test() {

    Calendar wednesday_usual_day = new GregorianCalendar();
    wednesday_usual_day.set(2018, 3, 2, 14, 5, 20);
    wednesday_usual_day.setWeekDate(2018, 9, 4);
    str_res = "=================== No work tests ===================\n"


    Calendar wednesday_usual_evening = new GregorianCalendar();
    wednesday_usual_evening.set(2018, 3, 2, 19, 5, 20);
    wednesday_usual_evening.setWeekDate(2018, 9, 4);
    str_res += no_work_test(wednesday_usual_evening, false)


    Calendar friday_usual_day = new GregorianCalendar();
    friday_usual_day.set(2018, 3, 2, 14, 5, 20);
    friday_usual_day.setWeekDate(2018, 9, 6);
    str_res += no_work_test(friday_usual_day, false)

    Calendar friday_usual_evening = new GregorianCalendar();
    friday_usual_evening.set(2018, 3, 2, 20, 5, 20);
    friday_usual_evening.setWeekDate(2018, 9, 6);
    str_res += no_work_test(friday_usual_evening, true)

    Calendar saturday_usual_day = new GregorianCalendar();
    saturday_usual_day.set(2018, 3, 2, 12, 5, 20);
    saturday_usual_day.setWeekDate(2018, 9, 7);
    str_res += no_work_test(saturday_usual_day, true)

    Calendar saturday_usual_evening = new GregorianCalendar();
    saturday_usual_evening.set(2018, 3, 2, 19, 5, 20);
    saturday_usual_evening.setWeekDate(2018, 9, 7);
    str_res += no_work_test(saturday_usual_evening, true)

    Calendar sunday_usual_morning = new GregorianCalendar();
    sunday_usual_morning.set(2018, 3, 2, 11, 5, 20);
    sunday_usual_morning.setWeekDate(2018, 9, 1);
    str_res += no_work_test(sunday_usual_morning, true)

    Calendar sunday_usual_evening = new GregorianCalendar();
    sunday_usual_evening.set(2018, 3, 2, 21, 5, 20);
    sunday_usual_evening.setWeekDate(2018, 9, 1);
    str_res += no_work_test(sunday_usual_evening, false)

    Calendar before_holiday_usual_day = new GregorianCalendar();
    before_holiday_usual_day.set(2018, 3, 2, 14, 5, 20);
    before_holiday_usual_day.setWeekDate(2018, 10, 4);
    str_res += no_work_test(before_holiday_usual_day, false)

    Calendar before_holiday_usual_evening = new GregorianCalendar();
    before_holiday_usual_evening.set(2018, 3, 2, 19, 5, 20);
    before_holiday_usual_evening.setWeekDate(2018, 10, 4);
    str_res += no_work_test(before_holiday_usual_evening, true)

    Calendar holiday_usual_day = new GregorianCalendar();
    holiday_usual_day.set(2018, 3, 2, 14, 5, 20);
    holiday_usual_day.setWeekDate(2018, 10, 5);
    str_res += no_work_test(holiday_usual_day, true)

    Calendar holiday_usual_evening = new GregorianCalendar();
    holiday_usual_evening.set(2018, 3, 2, 21, 5, 20);
    holiday_usual_evening.setWeekDate(2018, 10, 5);
    str_res += no_work_test(holiday_usual_evening, true)

    Calendar holiday_after_first_morning = new GregorianCalendar();
    holiday_after_first_morning.set(2018, 3, 2, 10, 5, 20);
    holiday_after_first_morning.setWeekDate(2018, 10, 6);
    str_res += no_work_test(holiday_after_first_morning, true)

    Calendar holiday_after_first_evening = new GregorianCalendar();
    holiday_after_first_evening.set(2018, 3, 2, 19, 5, 20);
    holiday_after_first_evening.setWeekDate(2018, 10, 6);
    str_res += no_work_test(holiday_after_first_evening, true)

    Calendar holiday_before_last_morning = new GregorianCalendar();
    holiday_before_last_morning.set(2018, 3, 2, 10, 5, 20);
    holiday_before_last_morning.setWeekDate(2018, 10, 7);
    str_res += no_work_test(holiday_before_last_morning, true)

    Calendar holiday_before_last_evening = new GregorianCalendar();
    holiday_before_last_evening.set(2018, 3, 2, 19, 5, 20);
    holiday_before_last_evening.setWeekDate(2018, 10, 7);
    str_res += no_work_test(holiday_before_last_evening, true)

    Calendar holiday_last_day = new GregorianCalendar();
    holiday_last_day.set(2018, 3, 2, 14, 5, 20);
    holiday_last_day.setWeekDate(2018, 11, 1);
    str_res += no_work_test(holiday_last_day, true)

    Calendar holiday_last_evening = new GregorianCalendar();
    holiday_last_evening.set(2018, 3, 2, 19, 5, 20);
    holiday_last_evening.setWeekDate(2018, 11, 1);
    str_res += no_work_test(holiday_last_evening, false)

    Calendar usual_first_day_morning = new GregorianCalendar();
    usual_first_day_morning.set(2018, 3, 2, 12, 5, 20);
    usual_first_day_morning.setWeekDate(2018, 11, 2);
    str_res += no_work_test(usual_first_day_morning, false)

    Calendar usual_first_day_evening = new GregorianCalendar();
    usual_first_day_evening.set(2018, 3, 2, 19, 5, 20);
    usual_first_day_evening.setWeekDate(2018, 11, 2);
    str_res += no_work_test(usual_first_day_evening, false)

    write_log(str_res)
}

//user_tests()
//no_work_test()