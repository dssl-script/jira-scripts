import com.atlassian.jira.bc.issue.IssueService
import com.atlassian.jira.component.ComponentAccessor;
import com.atlassian.jira.event.type.EventDispatchOption;
import com.atlassian.jira.issue.Issue
import com.atlassian.jira.issue.IssueInputParametersImpl;
import com.atlassian.jira.issue.IssueManager;
import com.atlassian.jira.issue.MutableIssue
import com.atlassian.jira.issue.UpdateIssueRequest;
import com.atlassian.jira.issue.comments.CommentManager;
import com.atlassian.jira.user.ApplicationUser;
import com.atlassian.jira.issue.comments.Comment;
import com.onresolve.scriptrunner.canned.jira.workflow.postfunctions.SendCustomEmail
import com.opensymphony.workflow.loader.ActionDescriptor
import com.opensymphony.workflow.loader.StepDescriptor
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

class Testss {
    IssueManager issueManager = ComponentAccessor.getIssueManager()
    Issue debug_issue = issueManager.getIssueObject("SM-77")
    //Issue debug_issue = issueManager.getIssueObject("SCR-1531")
    ApplicationUser debug_user = ComponentAccessor.getUserManager().getUserByName("v.monakhov")
    DebugIssueLogger dl = new DebugIssueLogger(debug_issue, debug_user);

    def run_test(event) {
        //dl.debug("TEST")
        //def changeItems = ComponentAccessor.changeHistoryManager.getChangeHistories(debug_issue)
        //dl.debug(changeItems[0].properties)
        def log = event.getChangeLog()
        dl.debug(log)
        /*
        for (int i = changeItems.size()-10; i < changeItems.size(); i++) {
            dl.debug(changeItems[i].getComment());
        }*/


        //dl.debug(changeItems)
        //WorkflowAssistant.do_action(debug_issue, "Открыть", debug_user);
    }
}

Testss ut = new Testss();
ut.run_test(event);

/*
issueManager = ComponentAccessor.getIssueManager()
Issue debug_issue = issueManager.getIssueObject("SCR-1531")
ApplicationUser debug_user = ComponentAccessor.getUserManager().getUserByName("v.monakhov")
def email_recievers = [
        "v.monakhov@dssl.ru"
        //"v.agafonov@dssl.ru",
        //"p.shwarts@dssl.ru"
]
DebugIssueLogger dl = new DebugIssueLogger(debug_issue, debug_user);
dl.setEmailRecievers(email_recievers);
*/