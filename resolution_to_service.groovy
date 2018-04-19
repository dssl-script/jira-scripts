
import com.atlassian.jira.issue.customfields.option.Options;
import com.atlassian.jira.issue.customfields.option.Option;
import com.atlassian.jira.component.ComponentAccessor;
import com.atlassian.jira.event.type.EventDispatchOption;
import com.atlassian.jira.issue.Issue;
import com.atlassian.jira.issue.IssueManager;
import com.atlassian.jira.issue.MutableIssue
import com.atlassian.jira.issue.UpdateIssueRequest;
import com.atlassian.jira.user.ApplicationUser;

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



class ToCommerceScript {
    IssueManager issueManager = ComponentAccessor.getIssueManager();
    Issue debug_issue = issueManager.getIssueObject("SCR-1604")
    ApplicationUser debug_user = ComponentAccessor.getUserManager().getUserByName("v.monakhov")
    String log_text;

    def email_recievers = [
            "v.monakhov@dssl.ru",
            "v.agafonov@dssl.ru",
            "p.shwarts@dssl.ru"
    ]
    DebugIssueLoggerCostyl dl = new DebugIssueLoggerCostyl(debug_issue, debug_user);

    ToCommerceScript() {
        dl.setEmailRecievers(email_recievers);
    }

    public void write_log(String text) {
        log_text += text;
    }

    def main(issue) {
        String text = "To commerce started...\n"
        ApplicationUser update_user = ComponentAccessor.getUserManager().getUserByName("v.agafonov")
        def resol = ComponentAccessor.getCustomFieldManager().getCustomFieldObject("customfield_11300")
        Options options = ComponentAccessor.getOptionsManager().getOptions(resol.getConfigurationSchemes().listIterator().next().getOneAndOnlyConfig());
        //options[6].getOptionId()
        if (options[5].getOptionId() != 11617) {
            throw new IllegalArgumentException ("Issue resolution search error (" + issue + ") " +
                    ": options[5] " + options[5].getOptionId() + options[5].getValue() + " not 'to service': " + options);
        }
        issue.setCustomFieldValue(resol, options[5])
        IssueUpdater.updateIssue(issue, update_user)
        text += "Issue: " + issue + "\n";
        text += "Resolution: " + options[5].getValue() + "\n";
        dl.write_comment_log(text)
        /*
        for (Option temp: options) {
            if (temp.getValue() == "Передано в коммерческий отдел") {
                issue.setCustomFieldValue(resol, temp)
                IssueUpdater.updateIssue(issue, update_user)
                text += "Issue: " + issue + "\n";
                text += "Resolution: " +  + "\n";
                dl.write_comment_log(text)
                option_found = true;
                break;
            }
        }
        */
        /*
        if (!option_found) {
            throw new IllegalArgumentException ("Issue resolution search error (" + issue + ") " +
                    ": resolution \"Передано в коммерческий отдел\" not found in options list: " + options);
        }
        */
    }



}


ToCommerceScript script = new ToCommerceScript();

//IssueManager issueManager = ComponentAccessor.getIssueManager()
//Issue issue = issueManager.getIssueObject("SM-77")


try {
    script.main(issue);
} catch (Exception e) {
    def script_name = "transitions_to_service " + this.class.getName()
    def ex_string = "ERROR in script " + script_name + ": " +  e +
            "; issue: " + issue
    script.dl.send_error_email("<font color=\"red\">" + ex_string + "</font>")
    script.dl.write_comment_log("{color:#FF0000}" + ex_string + "{color}")
}
