import com.atlassian.jira.component.ComponentAccessor;
import com.atlassian.jira.event.type.EventDispatchOption;
import com.atlassian.jira.issue.Issue
import com.atlassian.jira.issue.IssueManager;
import com.atlassian.jira.issue.MutableIssue
import com.atlassian.jira.issue.UpdateIssueRequest;
import com.atlassian.jira.issue.comments.CommentManager;
import com.atlassian.jira.user.ApplicationUser;
import com.atlassian.jira.issue.comments.Comment;
import com.onresolve.scriptrunner.canned.jira.workflow.postfunctions.SendCustomEmail
import org.apache.log4j.Logger
import org.apache.log4j.Level

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

class TestingEvent {
    Issue issue;
    ApplicationUser initiator;

    ApplicationUser getUser() {
        return initiator;
    }
}

class ResolutionSender {
    IssueManager issueManager = ComponentAccessor.getIssueManager()
    Issue debug_issue = issueManager.getIssueObject("SCR-1604");
    ApplicationUser debug_user = ComponentAccessor.getUserManager().getUserByName("v.monakhov");
    DebugIssueLogger dl = new DebugIssueLogger(debug_issue, debug_user);

    def ResolutionSender() {
        def email_recievers = [
                "v.monakhov@dssl.ru",
                //"v.agafonov@dssl.ru",
                //"p.shwarts@dssl.ru"
        ]
        dl.setEmailRecievers(email_recievers);
    }

    String get_text(sended_to) {
        String text = "Ваша заявка передана в " + sended_to + ".\n";
        text += "Ожидайте обратной связи."
        return text
    }

    void leave_comment(issue, user, sended_to) {
        CommentManager commentMgr = ComponentAccessor.getCommentManager()
        commentMgr.create(issue, user, get_text(sended_to), true)
    }

    void run(issue) {
        def dbg_string = "Send comment to client started\n";
        dbg_string += "Issue: " + issue + "\n";
        if (issue.getResolution().getName() == "Передано в сервисный центр") {
            dl.debug("to service")
            dbg_string += "to service, leaving comment"
            leave_comment(issue, issue.getAssignee(), "сервисный центр")
        }
        else if (issue.getResolution().getName() == "Передано в коммерческий отдел") {
            leave_comment(issue, issue.getAssignee(), "коммерческий отдел")
            dl.debug("to commerce")
            dbg_string += "to commerce, leaving comment"
        } else {
            dl.debug("resolution is " + issue.getResolution().getName() + "; break")
            return;
        }
        dl.write_comment_log(dbg_string);
    }


    def run_test() {
        TestingEvent event = new TestingEvent();

        event.issue = issueManager.getIssueObject("SM-77");
        event.initiator = debug_user;
        run(event.issue)
    }
}

ResolutionSender script = new ResolutionSender();



try {
    //script.run_test()
    script.run(issue);
} catch (Exception e) {
    def script_name = "to_commerce_service_mail " + this.class.getName()
    def ex_string = "ERROR in script " + script_name + ": " +  e +
            "; issue: " + issue
    script.dl.send_error_email(ex_string)
    script.dl.write_comment_log("{color:#FF0000}" + ex_string + "{color}")
}