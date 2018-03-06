import com.atlassian.jira.issue.Issue
import com.atlassian.jira.component.ComponentAccessor;
def issue = event.issue as Issue
def String summary = issue.summary
def String project = issue.projectObject.key
def changeItems = ComponentAccessor.changeHistoryManager.getAllChangeItems(issue)

import org.apache.log4j.Logger
import org.apache.log4j.Level

if (event.getUser().getName() == "v.monakhov") {
    Logger log = Logger.getLogger("com.acme.CreateSubtask");
    log.setLevel(Level.DEBUG);
    if (changeItems[changeItems.size()-1].getField() == "Маркеры") {
        log.debug "ZALUPA"
    }
}

