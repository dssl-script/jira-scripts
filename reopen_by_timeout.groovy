//import net.sf.hibernate.mapping.Component
import org.apache.log4j.Logger
import org.apache.log4j.Level



import java.text.SimpleDateFormat

import com.atlassian.jira.component.ComponentAccessor
import com.atlassian.jira.issue.search.SearchProvider
import com.atlassian.jira.jql.parser.JqlQueryParser
import com.atlassian.jira.web.bean.PagerFilter
import com.atlassian.jira.issue.MutableIssue
import com.atlassian.jira.issue.UpdateIssueRequest
import com.atlassian.jira.event.type.EventDispatchOption

import com.atlassian.jira.issue.DocumentIssueImpl

import com.atlassian.jira.bc.issue.IssueService
import com.atlassian.jira.issue.IssueInputParametersImpl

//import com.atlassian.jira.issue.fields

issueManager = ComponentAccessor.getIssueManager()
user = ComponentAccessor.getUserManager().getUserByName("v.agafonov")  //.getUser()

def get_issues_by_jql(jql_string) {
    def log = Logger.getLogger("com.acme.CreateSubtask")
    log.setLevel(Level.DEBUG)
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
    def log = Logger.getLogger("com.acme.CreateSubtask")
    log.setLevel(Level.DEBUG)
    IssueService issueService = ComponentAccessor.getIssueService()
    transitionValidationResult = issueService.validateTransition(issue.getAssignee(), issue.id, act_id,new IssueInputParametersImpl())
    transitionResult = issueService.transition(issue.getAssignee(), transitionValidationResult)
    if (transitionResult.isValid())
    { log.debug("Transitioned issue $issue through action $act_id") }
    updateIssue(issue)
}

def change_issues(jql_string, act_id) {
    def log = Logger.getLogger("com.acme.CreateSubtask")
    log.setLevel(Level.DEBUG)
    log.debug "Change issues script started"
    def issues_list = get_issues_by_jql(jql_string)
    for (DocumentIssueImpl x : issues_list) {
        def issue = issueManager.getIssueObject(x.key)
        change_workflow_action(act_id,issue)
    }
}

def updateIssue(MutableIssue issue) {
    issueManager.updateIssue(user, issue, createIssueUpdateRequest())
}

def createIssueUpdateRequest() {
    new UpdateIssueRequest.UpdateIssueRequestBuilder()
            .eventDispatchOption(EventDispatchOption.DO_NOT_DISPATCH)
            .sendMail(false)
            .build()
}

jql_string_to_open = 'project not in (Script, JST) AND status not in (Closed, "In Progress",Open,Resolved) AND "Next Step Date"  < 10m'

change_issues(jql_string_to_open, 51)
//get_custom_field()