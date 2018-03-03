//import net.sf.hibernate.mapping.Component
import org.apache.log4j.Logger
import org.apache.log4j.Level

log = Logger.getLogger("com.acme.CreateSubtask")
log.setLevel(Level.DEBUG)

import java.text.SimpleDateFormat

import com.atlassian.jira.component.ComponentAccessor
import com.atlassian.jira.issue.search.SearchProvider
import com.atlassian.jira.jql.parser.JqlQueryParser
import com.atlassian.jira.web.bean.PagerFilter
import com.atlassian.jira.issue.MutableIssue
import com.atlassian.jira.issue.UpdateIssueRequest
import com.atlassian.jira.event.type.EventDispatchOption

import com.atlassian.jira.issue.DocumentIssueImpl

import com.opensymphony.workflow.loader.StepDescriptor
import com.opensymphony.workflow.loader.ActionDescriptor

import com.atlassian.jira.bc.issue.IssueService
import com.atlassian.jira.issue.IssueInputParametersImpl

import com.atlassian.jira.issue.CustomFieldManager
//import com.atlassian.jira.issue.fields

issueManager = ComponentAccessor.getIssueManager()
user = ComponentAccessor.getUserManager().getUserByName("v.agafonov")  //.getUser()

def test_run() {
    //def issueManager = ComponentAccessor.issueManager
    def issue = issueManager.getIssueObject("TEST-1")
    def date = new Date()
    sdf = new SimpleDateFormat("MM/dd/yyyy HH:mm:ss")
    issue.setDescription(sdf.format(date))
    issue.setIssueTypeId("10006")

    //issue.setIssueTypeId()
    updateIssue(issue)
    //issue.setDescription("TESTING")
    //issue.store()
}

def get_actions_by_issue(MutableIssue issue) {
    def workflow = ComponentAccessor.getWorkflowManager().getWorkflow(issue)

    StepDescriptor oStep = workflow.getLinkedStep(issue.getStatusObject());
    List<ActionDescriptor> oActions = oStep.getActions();
    def text = "$issue.key actions - "
    for(ActionDescriptor oAction : oActions)
    {
        text += oAction.getName() + ":" + oAction.getId() + ";"
    }
    return text
}

def get_issues_by_jql(jql_string) {
    log.debug("JQL string: '$jql_string'")

    def jqlQueryParser = ComponentAccessor.getComponent(JqlQueryParser)
    def searchProvider = ComponentAccessor.getComponent(SearchProvider)
    def query = jqlQueryParser.parseQuery(jql_string)
    def results = searchProvider.search(query, user, PagerFilter.getUnlimitedFilter())
    def res = results.getIssues()
    log.debug("JQL res size: $res.size")
    return res
}


def change_workflow_action(act_id, issue) {
    IssueService issueService = ComponentAccessor.getIssueService()
    transitionValidationResult = issueService.validateTransition(issue.getAssignee(), issue.id, act_id,new IssueInputParametersImpl())
    transitionResult = issueService.transition(issue.getAssignee(), transitionValidationResult)
    updateIssue(issue)
}

def collect_info(jql_string) {
    def issues_list = get_issues_by_jql(jql_string)
    for (DocumentIssueImpl x : issues_list) {
        def issue = issueManager.getIssueObject(x.key)
        log.debug get_actions_by_issue(issue)
    }
}

def change_issues(jql_string, act_id) {
    def issues_list = get_issues_by_jql(jql_string)
    for (DocumentIssueImpl x : issues_list) {
        def issue = issueManager.getIssueObject(x.key)
        change_workflow_action(act_id,issue)
    }
}

def get_custom_field() {
    def issue = issueManager.getIssueObject("TEST-1")
    def customFieldManager = ComponentAccessor.getCustomFieldManager()
    def cField = customFieldManager.getCustomFieldObject("customfield_10200")
    def cFieldValue = issue.getCustomFieldValue(cField)
    return cFieldValue
}

def updateIssue(MutableIssue issue) {
    //def user = ComponentAccessor.jiraAuthenticationContext.user
    //def issueManager = ComponentAccessor.issueManager
    issueManager.updateIssue(user, issue, createIssueUpdateRequest())
}

def createIssueUpdateRequest() {
    new UpdateIssueRequest.UpdateIssueRequestBuilder()
            .eventDispatchOption(EventDispatchOption.DO_NOT_DISPATCH)
            .sendMail(false)
            .build()
}

collect_info('project = test')
//change_issues('project = test and status = "In Progress"', 31)
//get_custom_field()