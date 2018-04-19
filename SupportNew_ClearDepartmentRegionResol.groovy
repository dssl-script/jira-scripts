import com.atlassian.jira.component.ComponentAccessor
import com.atlassian.jira.issue.Issue
import com.atlassian.jira.issue.IssueManager
import com.atlassian.jira.user.ApplicationUser
import com.atlassian.jira.issue.fields.CustomField;

def run(issue) {
    ApplicationUser update_user = ComponentAccessor.getUserManager().getUserByName("v.agafonov")

    CustomField department = ComponentAccessor.getCustomFieldManager().getCustomFieldObject("customfield_12019")
    CustomField region = ComponentAccessor.getCustomFieldManager().getCustomFieldObject("customfield_12017")
    CustomField resol = ComponentAccessor.getCustomFieldManager().getCustomFieldObject("customfield_11300")

    issue.setCustomFieldValue(department, null)
    issue.setCustomFieldValue(region, null)
    issue.setCustomFieldValue(resol, null)

    IssueUpdater.updateIssue(issue, update_user)

}

//IssueManager issueManager = ComponentAccessor.getIssueManager()
//Issue debug_issue = issueManager.getIssueObject("JST-2");

run(issue)
