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

    static boolean to_unassigned(user_assigned, c_day) {
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
    def email_recievers = [
            "v.monakhov@dssl.ru"
            //"v.agafonov@dssl.ru",
            //"p.shwarts@dssl.ru"
    ]
    DebugIssueLogger dl = new DebugIssueLogger(debug_issue, debug_user);

    CommentReopener() {
        dl.setEmailRecievers(email_recievers);
    }

    public void test_event() {
        TestingEvent test_ev = new TestingEvent();
        IssueManager issueManager = ComponentAccessor.getIssueManager()
        Issue test_issue = issueManager.getIssueObject("SM-77")
        Calendar c_day = new GregorianCalendar();
        test_ev.issue = test_issue;
        test_ev.initiator = ComponentAccessor.getUserManager().getUserByName("0037@okskoe.com")

        UnassignLogic.to_unassigned(test_ev.issue.getAssigneeUser(), c_day);
        dl.debug "test"
    }

}

CommentReopener script = new CommentReopener();
script.test_event();

