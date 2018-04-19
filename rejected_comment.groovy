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

IssueManager issueManager = ComponentAccessor.getIssueManager()
Issue debug_issue = issueManager.getIssueObject("SM-77")
//Issue debug_issue = issueManager.getIssueObject("SCR-1531")
ApplicationUser debug_user = ComponentAccessor.getUserManager().getUserByName("v.monakhov")
DebugIssueLogger dl = new DebugIssueLogger(debug_issue, debug_user);

def attachmentManager = ComponentAccessor.getAttachmentManager()
def attch = attachmentManager.getAttachments(debug_issue)
dl.debug(attch)

def test1() {
    def file = new File("/var/atlassian/application-data/jira/scripts/test.html")

    res = new URL("http://support.trassir.com/si/jira.issueviews:issue-html/SM-77/SM-77.html").getText()
    file.write res
    String text = "test"
    def email_recievers = [
            "1202@dssl.ru"
            //"v.agafonov@dssl.ru",
            //"p.shwarts@dssl.ru"
    ]
    return System.getProperty("user.dir");
}

//test1()

def test2() {

    //dl.debug "what " + issue
    //String text = new URL("http://support.trassir.com/si/jira.issueviews:issue-html/SM-77/SM-77.html").getText()
    dl.debug new URL("http://support.trassir.com/si/jira.issueviews:issue-html/SM-77/SM-77.html").getText()
    String text = "test"
    def email_recievers = [
            "1202@dssl.ru"
            //"v.agafonov@dssl.ru",
            //"p.shwarts@dssl.ru"
    ]


    String subject = debug_issue.getKey() + " " + debug_issue.getSummary();
    def params = [
            "issue"                                       : debug_issue,
            (SendCustomEmail.FIELD_EMAIL_TEMPLATE)        : text,
            (SendCustomEmail.FIELD_EMAIL_SUBJECT_TEMPLATE): "test",
            (SendCustomEmail.FIELD_TO_ADDRESSES)          : String.join(",", email_recievers),
            (SendCustomEmail.FIELD_EMAIL_FORMAT)          : "HTML"
    ]
    SendCustomEmail sendCustomEmail = new SendCustomEmail();
    if (!sendCustomEmail.doValidate(params, false).hasAnyErrors()) {
        sendCustomEmail.doScript(params);
    } else {
        err_string = "{color:#FF0000}SEND MAIL ERROR: " + sendCustomEmail.doValidate(params, false).errors + "{color}"

    }
}