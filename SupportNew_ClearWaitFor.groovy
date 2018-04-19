import com.atlassian.jira.component.ComponentAccessor
import com.atlassian.jira.issue.Issue
import com.atlassian.jira.issue.IssueManager
import com.atlassian.jira.user.ApplicationUser
import com.atlassian.jira.issue.fields.CustomField;

def run(issue) {
    ApplicationUser update_user = ComponentAccessor.getUserManager().getUserByName("v.agafonov")

    CustomField waitfor = ComponentAccessor.getCustomFieldManager().getCustomFieldObject("customfield_11301")

    issue.setCustomFieldValue(waitfor, null)

    IssueUpdater.updateIssue(issue, update_user)

}

//IssueManager issueManager = ComponentAccessor.getIssueManager()
//Issue debug_issue = issueManager.getIssueObject("JST-2");

run(issue)
