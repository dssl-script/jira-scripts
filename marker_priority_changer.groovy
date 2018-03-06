import com.atlassian.jira.component.ComponentAccessor
import com.atlassian.jira.issue.Issue
import com.atlassian.jira.issue.IssueManager
import com.atlassian.jira.issue.comments.Comment
import com.atlassian.jira.issue.comments.CommentManager
import com.atlassian.jira.user.ApplicationUser
import org.apache.log4j.Logger
import org.apache.log4j.Level
import com.onresolve.scriptrunner.canned.jira.workflow.postfunctions.SendCustomEmail

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


class TestingEvent {
    Issue issue;
    ApplicationUser initiator;

    ApplicationUser getUser() {
        return initiator;
    }
}

class MarkerPriorityChanger {
    IssueManager issueManager = ComponentAccessor.getIssueManager();
    Issue debug_issue = issueManager.getIssueObject("SCR-1552")
    ApplicationUser debug_user = ComponentAccessor.getUserManager().getUserByName("v.monakhov")
    String log_str;

    def email_recievers = [
            "v.monakhov@dssl.ru",
            "v.agafonov@dssl.ru",
            "p.shwarts@dssl.ru"
    ]
    DebugIssueLoggerCostyl dl = new DebugIssueLoggerCostyl(debug_issue, debug_user);

    MarkerPriorityChanger() {
        dl.setEmailRecievers(email_recievers);
    }

    def in_except_list(issue) {
        def exept_proj = [
                "SCR",
                "TOEXPERT"
        ]
        for (def x: exept_proj) {
            if (x == issue.getProjectObject().getKey()) {
                return true
            }
        }
        return false
    }

    def start(event) {
        log_str = "Issue: " + event.issue + "\n"
        if (in_except_list(event.issue)) {
            log_str += "project " + event.issue.getProjectObject().getKey() + " in except list, break\n"
        } else {
            def priority_objects_list = ComponentAccessor.getConstantsManager().getPriorityObjects()
            def priority_markers = ComponentAccessor.getCustomFieldManager().getCustomFieldObject("customfield_11400")
            def priority_markers_value = event.issue.getCustomFieldValue(priority_markers)
            log_str += "Markers: " + priority_markers_value + "\n"
            if (!priority_markers_value) {
                event.issue.setPriorityId(priority_objects_list.findByName("Lowest").id)
                log_str += "markers is null, to lowest" + "\n"
            } else if (priority_markers_value.size() == 1) {
                event.issue.setPriorityId(priority_objects_list.findByName("Low").id)
                log_str += "markers size is 1, to low" + "\n"
            } else if (priority_markers_value.size() == 2) {
                event.issue.setPriorityId(priority_objects_list.findByName("Medium").id)
                log_str += "markers size is 2, to Medium" + "\n"
            } else if (priority_markers_value.size() == 3) {
                event.issue.setPriorityId(priority_objects_list.findByName("High").id)
                log_str += "markers size is 3, to High" + "\n"
            } else if (priority_markers_value.size() >= 4) {
                event.issue.setPriorityId(priority_objects_list.findByName("Highest").id)
                log_str += "markers size is 4 or more, to Highest" + "\n"
            }
            def update_user = ComponentAccessor.getUserManager().getUserByName("v.agafonov")
            IssueUpdater.updateIssue(event.issue, update_user)
        }
        dl.write_comment_log(log_str)
        dl.debug log_str
    }

    def run_test() {
        TestingEvent test_ev = new TestingEvent();
        IssueManager issueManager = ComponentAccessor.getIssueManager()
        Issue test_issue = issueManager.getIssueObject("SM-77")
        //Issue test_issue = issueManager.getIssueObject("SCR-1531")
        //Issue test_issue = issueManager.getIssueObject("TOEXPERT-12")
        test_ev.issue = test_issue;
        test_ev.initiator = ComponentAccessor.getUserManager().getUserByName("v.monakhov")
        start(test_ev)
    }
}


MarkerPriorityChanger script = new MarkerPriorityChanger()

try {
    //script.run_test()
    script.start(event);
} catch (Exception e) {
    def script_name = "marker_priority_changer " + this.class.getName()
    def ex_string = "ERROR in script " + script_name + ": " +  e +
            "; issue: " + event.issue
    script.dl.send_error_email(ex_string)
    script.dl.write_comment_log("{color:#FF0000}" + ex_string + "{color}")
}