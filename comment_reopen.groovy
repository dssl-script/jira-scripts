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
import org.hsqldb.lib.Collection

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

import com.atlassian.crowd.embedded.api.Group

import com.atlassian.jira.component.ComponentAccessor;
import com.atlassian.jira.event.type.EventDispatchOption;
import com.atlassian.jira.issue.Issue;
import com.atlassian.jira.issue.IssueManager;
import com.atlassian.jira.issue.MutableIssue
import com.atlassian.jira.issue.UpdateIssueRequest;
import com.atlassian.jira.issue.comments.CommentManager;
import com.atlassian.jira.user.ApplicationUser;
import com.atlassian.jira.issue.comments.Comment;
import com.onresolve.scriptrunner.canned.jira.workflow.postfunctions.SendCustomEmail;

import org.apache.log4j.Logger
import org.apache.log4j.Level

import com.atlassian.jira.workflow.JiraWorkflow;


class DebugIssueLogger {
    CommentManager commentMgr = ComponentAccessor.getCommentManager()
    Issue debug_issue;
    ApplicationUser debug_user;
    int max_comments = 200;
    String[] email_recievers;

    Logger log = Logger.getLogger("com.acme.CreateSubtask");

    DebugIssueLogger() {
        log.setLevel(Level.DEBUG);
    }

    DebugIssueLogger(Issue issue, ApplicationUser user) {
        debug_issue = issue;
        debug_user = user;
        log.setLevel(Level.DEBUG);
    }

    private void clear_old_comments(List<Comment> comments) {
        int num_to_delete = comments.size() - (max_comments-1);
        for (int i=0; i<num_to_delete; i++) {
            commentMgr.delete(comments[i])
        }
    }

    public void debug(text) {
        log.debug(text);
    }

    public void setEmailRecievers(recievers) {
        email_recievers = recievers;
    }

    public void write_comment_log(text) {
        List<Comment> comments = commentMgr.getComments(debug_issue);
        clear_old_comments(comments)
        commentMgr.create(debug_issue, debug_user, text, false)
    }

    public void write_to_body_log(text) {
        debug_issue.setDescription(text)
        IssueUpdater.updateIssue(debug_issue, debug_user)
    }

    public void send_error_email(text) {
        String subject = debug_issue.getKey() + " " + debug_issue.getSummary();
        def params = [
                "issue"                                       : debug_issue,
                (SendCustomEmail.FIELD_EMAIL_TEMPLATE)        : "<font color=\"red\">" + text + "</font>",
                (SendCustomEmail.FIELD_EMAIL_SUBJECT_TEMPLATE): subject,
                (SendCustomEmail.FIELD_TO_ADDRESSES)          : String.join(",", email_recievers),
                (SendCustomEmail.FIELD_EMAIL_FORMAT)          : "HTML"
        ]
        SendCustomEmail sendCustomEmail = new SendCustomEmail();
        if (!sendCustomEmail.doValidate(params, false).hasAnyErrors()) {
            sendCustomEmail.doScript(params);
        } else {
            err_string = "{color:#FF0000}SEND MAIL ERROR: " + sendCustomEmail.doValidate(params, false).errors + "{color}"
            write_comment_log(err_string)
        }

    }
}

class IssueUpdater {

    static void updateIssue(MutableIssue issue, user) {
        IssueManager issueManager = ComponentAccessor.getIssueManager()
        issueManager.updateIssue(user, issue, IssueUpdater.createIssueUpdateRequest())
    }

    static UpdateIssueRequest createIssueUpdateRequest() {
        new UpdateIssueRequest.UpdateIssueRequestBuilder()
                .eventDispatchOption(EventDispatchOption.DO_NOT_DISPATCH)
                .sendMail(false)
                .build()
    }
}


class WorkflowAssistant {
    private static int get_action_id_by_issue(MutableIssue issue, String action_name) {
        JiraWorkflow workflow = ComponentAccessor.getWorkflowManager().getWorkflow(issue)

        StepDescriptor oStep = workflow.getLinkedStep(issue.getStatusObject());
        List<ActionDescriptor> oActions = oStep.getActions()
        for(ActionDescriptor oAction : oActions)
        {
            if (oAction.getName() == action_name) return oAction.getId()
        }
        throw new IllegalArgumentException ("Issue action search error (" + issue + ") " +
                ": action " + action_name + " not found in available actions list: " + oActions);
    }

    private static boolean transit_issue(issue, action_id, action_user) {
        IssueService issueService = ComponentAccessor.getIssueService()
        ApplicationUser debug_user = ComponentAccessor.getUserManager().getUserByName("0037@okskoe.com")
        IssueService.TransitionValidationResult transitionValidationResult = issueService.
                validateTransition(action_user, issue.id, action_id, new IssueInputParametersImpl())
        if (transitionValidationResult.isValid()) {
            IssueService.IssueResult transitionResult = issueService.transition(action_user, transitionValidationResult)
            if (transitionResult.isValid()) {
                return true
            } else {
                throw new IllegalArgumentException ("Issue transition error (" + issue + ") " +
                        " from " + issue.getStatusObject().getName() + ": " + transitionResult.getErrorCollection());
            }
        } else {
            throw new IllegalArgumentException ("Issue transitionValidation error (" + issue + ") " +
                    " from " + issue.getStatusObject().getName() + ": " + transitionValidationResult.getErrorCollection());
        }
    }

    public static boolean do_action(MutableIssue issue, String action_name, ApplicationUser action_user) {
        int action_id = get_action_id_by_issue(issue, action_name);
        boolean  res = transit_issue(issue, action_id, action_user)
        return res;
    }
}


class DebugIssueLoggerCostyl {
    CommentManager commentMgr = ComponentAccessor.getCommentManager()
    Issue debug_issue;
    ApplicationUser debug_user;
    int max_comments = 200;
    String[] email_recievers;

    Logger log = Logger.getLogger("com.acme.CreateSubtask");

    DebugIssueLoggerCostyl() {
        log.setLevel(Level.DEBUG);
    }

    DebugIssueLoggerCostyl(Issue issue, ApplicationUser user) {
        debug_issue = issue;
        debug_user = user;
        log.setLevel(Level.DEBUG);
    }

    private void clear_old_comments(List<Comment> comments) {
        int num_to_delete = comments.size() - (max_comments-1);
        for (int i=0; i<num_to_delete; i++) {
            commentMgr.delete(comments[i])
        }
    }

    public void debug(text) {
        log.debug(text);
    }

    public void setEmailRecievers(recievers) {
        email_recievers = recievers;
    }

    public void write_comment_log(text) {
        List<Comment> comments = commentMgr.getComments(debug_issue);
        clear_old_comments(comments)
        commentMgr.create(debug_issue, debug_user, text, false)
    }

    public void write_to_body_log(text) {
        debug_issue.setDescription(text)
        IssueUpdater.updateIssue(debug_issue, debug_user)
    }

    public void send_error_email(text) {
        String subject = debug_issue.getKey() + " " + debug_issue.getSummary();
        def params = [
                "issue"                                       : debug_issue,
                (SendCustomEmail.FIELD_EMAIL_TEMPLATE)        : "<font color=\"red\">" + text + "</font>",
                (SendCustomEmail.FIELD_EMAIL_SUBJECT_TEMPLATE): subject,
                (SendCustomEmail.FIELD_TO_ADDRESSES)          : String.join(",", email_recievers),
                (SendCustomEmail.FIELD_EMAIL_FORMAT)          : "HTML"
        ]
        SendCustomEmail sendCustomEmail = new SendCustomEmail();
        if (!sendCustomEmail.doValidate(params, false).hasAnyErrors()) {
            sendCustomEmail.doScript(params);
        } else {
            err_string = "{color:#FF0000}SEND MAIL ERROR: " + sendCustomEmail.doValidate(params, false).errors + "{color}"
            write_comment_log(err_string)
        }

    }
}



class UnassignLogic {

    static boolean is_holiday(c_day) {
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

    static boolean is_no_work(c_day) {
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

    static boolean has_assign_group(groups) {
        for (int i; i<groups.size(); i++) {
            if (groups[i].getName() == "sd-OnLine"  ||
                    groups[i].getName() == "sd-OffLine" ||
                    groups[i].getName() == "sd-absent") {
                return true
            }
        }
    }

    static boolean to_unassigned(ApplicationUser user_assigned, Calendar c_day) {
        def groups_assigned = ComponentAccessor.getGroupManager().getGroupsForUser(user_assigned)
            if (is_no_work(c_day)) {
                return true
            } else if (!is_no_work(c_day) && has_assign_group(groups_assigned)) {
                return true
            }
        return false
    }
}

class TestingEvent {
    Issue issue;
    ApplicationUser initiator;

    ApplicationUser getUser() {
        return initiator;
    }
}



class CommentReopener {
    IssueManager issueManager = ComponentAccessor.getIssueManager();
    Issue debug_issue = issueManager.getIssueObject("SCR-1531")
    ApplicationUser debug_user = ComponentAccessor.getUserManager().getUserByName("v.monakhov")
    String log_text;

    def email_recievers = [
            "v.monakhov@dssl.ru",
            "v.agafonov@dssl.ru",
            "p.shwarts@dssl.ru"
    ]
    DebugIssueLoggerCostyl dl = new DebugIssueLoggerCostyl(debug_issue, debug_user);

    CommentReopener() {
        dl.setEmailRecievers(email_recievers);
    }

    public void write_log(String text) {
        log_text += text;
    }

    public void main(event, Calendar c_day) {
        DateFormat dateFormatTest = new SimpleDateFormat("dd.MM.yyyy HH:mm:SS");
        log_text = "";

        //ArrayList<Group> groups_initiator = ComponentAccessor.getGroupManager().getGroupsForUser(event.getUser())
        write_log("\nTime: " + get_day_string(c_day.get(Calendar.DAY_OF_WEEK)) +
                  " " + dateFormatTest.format(c_day.getTime()) + "\n");
        write_log("Issue: " + event.issue + "\n")
        write_log("Initiator: " + event.getUser() + "\n")
        //write_log("Initiator groups: " + groups_initiator + "\n")
        write_log("Assignee user: " + event.issue.getAssigneeUser() + "\n")
        if (event.issue.getProjectObject().getKey() == "TOEXPERT") {
            write_log("Project TOEXPERT, break")
            dl.debug log_text;
            return
        }
        //if (!groups_initiator.size() || event.getUser().getName() == "callcenter") {
        if (event.getUser() != event.issue.getAssigneeUser()) {
            ApplicationUser update_user = ComponentAccessor.getUserManager().getUserByName("v.agafonov")
            if (event.issue.getStatusObject().getName() == "Open") {
                write_log("Issue already opened\n")
            } else {
                WorkflowAssistant.do_action(event.issue, "Открыть", update_user);
                write_log("Issue translated to Open\n")
            }
            boolean res;
            if (event.issue.getAssigneeUser() && event.issue.getProjectObject().getKey() != "SCR") {
                res = UnassignLogic.to_unassigned(event.issue.getAssigneeUser(), c_day);
                write_log("To unassign: " + res + "\n")
            } else {
                res = false
                write_log("To unassign: " + res + ", assignee is null or SCR\n")
            }

            if (res) {
                event.issue.setAssigneeId(null);
                IssueUpdater.updateIssue(event.issue, update_user)
            }
            dl.write_comment_log(log_text);
        } else {
            write_log("Initiator equal to assignee, break\n");
        }
        dl.debug log_text;
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

    public void test_event() {
        TestingEvent test_ev = new TestingEvent();
        IssueManager issueManager = ComponentAccessor.getIssueManager()
        Issue test_issue = issueManager.getIssueObject("SM-77")
        //Issue test_issue = issueManager.getIssueObject("SCR-1531")
        //Issue test_issue = issueManager.getIssueObject("SCR-1529")
        test_ev.issue = test_issue;
        //test_ev.initiator = ComponentAccessor.getUserManager().getUserByName("0037@okskoe.com")
        test_ev.initiator = ComponentAccessor.getUserManager().getUserByName("v.monakhov")
        Calendar c_day = new GregorianCalendar(2018,3,18,20,15,0)
        main(test_ev, c_day)
        Calendar c_day1 = new GregorianCalendar(2018,3,19,20,15,0)
        main(test_ev, c_day1)
        Calendar c_day2 = new GregorianCalendar(2018,3,20,20,15,0)
        main(test_ev, c_day2)
        Calendar c_day3 = new GregorianCalendar(2018,3,21,20,15,0)
        main(test_ev, c_day3)
        Calendar c_day4 = new GregorianCalendar(2018,3,22,20,15,0)
        main(test_ev, c_day4)
        //c_day.set(Calendar.DAY_OF_WEEK, Calendar.TUESDAY);

        //c_day.setWeekDate(2018, 10, 5);
        //UnassignLogic.to_unassigned(test_ev.issue.getAssigneeUser(), c_day);


    }

}


Calendar c_day = new GregorianCalendar();
CommentReopener script = new CommentReopener();

try {
    //script.test_event();
    script.main(event, c_day);
} catch (Exception e) {
    def script_name = "update_reopen " + this.class.getName()
    def ex_string = "ERROR in script " + script_name + ": " +  e +
            "; issue: " + event.issue
    script.dl.send_error_email("<font color=\"red\">" + ex_string + "</font>")
    script.dl.write_comment_log("{color:#FF0000}" + ex_string + "{color}")
}