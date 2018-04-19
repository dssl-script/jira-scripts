import com.atlassian.jira.component.ComponentAccessor
import com.atlassian.jira.issue.Issue
import com.atlassian.jira.issue.IssueManager
import com.atlassian.jira.issue.comments.Comment
import com.atlassian.jira.issue.comments.CommentManager
import com.atlassian.jira.issue.search.SearchProvider
import com.atlassian.jira.jql.parser.JqlQueryParser
import com.atlassian.jira.user.ApplicationUser
import com.atlassian.jira.web.bean.PagerFilter
import com.atlassian.jira.issue.MutableIssue
import com.onresolve.scriptrunner.canned.jira.workflow.postfunctions.SendCustomEmail
import com.atlassian.jira.bc.issue.IssueService
import com.atlassian.jira.issue.IssueInputParametersImpl
import com.atlassian.jira.workflow.JiraWorkflow
import com.opensymphony.workflow.loader.ActionDescriptor
import com.opensymphony.workflow.loader.StepDescriptor
import org.apache.log4j.Level
import org.apache.log4j.Logger

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

public class DebugLog {
    Logger log;

    DebugLog() {
        log = Logger.getLogger("com.acme.CreateSubtask");
        log.setLevel(Level.DEBUG);
    }

    public void debug(text) {
        log.debug(text)
    }
}


class DebugIssueLogger {
    CommentManager commentMgr = ComponentAccessor.getCommentManager()
    Issue debug_issue;
    ApplicationUser debug_user;
    int max_comments = 200;
    String[] email_recievers;

    DebugIssueLogger(Issue issue, ApplicationUser user) {
        debug_issue = issue;
        debug_user = user;
    }

    private void clear_old_comments(List<Comment> comments) {
        int num_to_delete = comments.size() - (max_comments-1);
        for (int i=0; i<num_to_delete; i++) {
            commentMgr.delete(comments[i])
        }
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

class ReopenByTimeoutScr {
    IssueManager issueManager = ComponentAccessor.getIssueManager();
    Issue debug_issue = issueManager.getIssueObject("SCR-1718")
    ApplicationUser debug_user = ComponentAccessor.getUserManager().getUserByName("v.monakhov")

    def updateUser = ComponentAccessor.getUserManager().getUserByName("v.agafonov")  //.getUser()
    DebugLog dbg;
    public DebugIssueLogger dl;

    ReopenByTimeoutScr() {
        dbg = new DebugLog();
        dl = new DebugIssueLogger(debug_issue, debug_user)
        dl.email_recievers = [
                "v.monakhov@dssl.ru",
        ]
    }

    def get_issues_by_jql(jql_string) {
        def query = ComponentAccessor.getComponent(JqlQueryParser).parseQuery(jql_string);
        def results = ComponentAccessor.getComponent(SearchProvider).search(query, updateUser, PagerFilter.getUnlimitedFilter());
        return results.getIssues()
    }

    def run() {
        String log_text = ""
        def jql_string_to_open = 'project not in (Script) AND status not in (Closed, "In Progress",Open,Resolved) AND "Next Step Date"  < 10m'
        def issues = get_issues_by_jql(jql_string_to_open);
        log_text += "Issues: " + issues.toString() + "\n";
        for (def x : issues) {
            def issue = issueManager.getIssueObject(x.key)
            log_text += "Issue " + issue.getKey() + " do action 'Открыть'\n"
            try {
                WorkflowAssistant.do_action(issue, "Открыть", updateUser);
            } catch (Exception exc) {
                log_text += "{color:#FF0000}ERROR: " + exc + "{color}\n"
            }
        }
        log_text += "END"
        dl.write_comment_log(log_text)
    }
}

ReopenByTimeoutScr script = new ReopenByTimeoutScr();
try {
    script.run()
} catch (Exception e) {
    def script_name = "ReopenByTimeout " + this.class.getName()
    def ex_string = "ERROR in script " + script_name + ": " + e
    script.dl.send_error_email("<font color=\"red\">" + ex_string + "</font>")
    script.dl.write_comment_log("{color:#FF0000}" + ex_string + "{color}")
}