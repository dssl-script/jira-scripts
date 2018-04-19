import com.atlassian.jira.component.ComponentAccessor
import com.atlassian.jira.issue.Issue
import com.atlassian.jira.issue.IssueManager
import com.atlassian.jira.issue.comments.Comment;
import com.atlassian.jira.issue.comments.CommentManager
import com.atlassian.jira.user.ApplicationUser;
import com.onresolve.scriptrunner.canned.jira.workflow.postfunctions.SendCustomEmail
import groovy.text.GStringTemplateEngine
import org.apache.log4j.Level
import org.apache.log4j.Logger

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

class SendToDepartment {
    IssueManager issueManager = ComponentAccessor.getIssueManager()
    Issue debug_issue = issueManager.getIssueObject("SCR-1604");
    ApplicationUser debug_user = ComponentAccessor.getUserManager().getUserByName("v.monakhov");
    DebugIssueLogger dl = new DebugIssueLogger(debug_issue, debug_user);
    CommentManager commentMgr = ComponentAccessor.getCommentManager();

    def SendToDepartment() {
        def email_recievers = [
                "v.monakhov@dssl.ru",
                //"v.agafonov@dssl.ru",
                //"p.shwarts@dssl.ru"
        ]
        dl.setEmailRecievers(email_recievers);
    }

    def isNewSentence(buf) {
        int asciCode = (int) buf[2];
        if (asciCode > 1039 && asciCode < 1072) {
            return true;
        } else {
            return false;
        }
    }

    def formatText(String text) {
        text = text.replaceAll("\r", "<br>");
        String new_text = "";
        boolean inTag = false;
        boolean inBlock = false;
        for (int i =0; i<text.length(); i++) {
            char temp = text.charAt(i);
            if (temp == '{') {
                inTag = true;
            }
            if (temp == '}') {
                inTag = false;
                continue;
            }
            if (temp == '[') {
                inBlock = true;
            }
            if (temp == ']') {
                inBlock = false;
                continue;
            }
            if (!inTag && !inBlock) {
                new_text += temp;
            }
            if (temp == '.' || temp == '!') {
                int start = i;
                int end = i+3;
                try {
                    def buf = text[start..end];
                    if (isNewSentence(buf)) {
                        new_text += "<br>";
                    }
                } catch (Exception e) {
                    // pass
                }
            }
        }
        return new_text;
    }

    def run(issue) {
        def allComments = commentMgr.getComments(issue)
        def last_comment = allComments[allComments.size()-1];

        def engine = new groovy.text.GStringTemplateEngine()

        def devices_check = ComponentAccessor.getCustomFieldManager().getCustomFieldObject("customfield_10808").getValue(issue)
        if (!devices_check) {
            devices_check = "Не указаны"
        }

        def contacts_check = ComponentAccessor.getCustomFieldManager().getCustomFieldObject("customfield_10803").getValue(issue)
        if (!contacts_check) {
            contacts_check = "Не указаны"
        }


        def attachment_text = ""
        for (x in issue.getAttachments()) {
            attachment_text += x.getFilename() + "<br>"
        }

        def assignee_name = "Нет"
        if (issue.getAssignee()) {
            assignee_name = issue.getAssignee().getDisplayName()
        }

        def binding = [
                mail_author : last_comment.getAuthorFullName(),
                mail_date : (last_comment.getUpdated()),
                mail_body : formatText(last_comment.getBody()),
                description : (formatText(issue.getDescription())),
                devices : (devices_check),
                contacts : (contacts_check),
                author : (issue.getReporter().getName()),
                author_mail : (issue.getReporter().getEmailAddress()),
                assignee : assignee_name,
                issue : (issue.getKey()),
                attachment : attachment_text
        ]


        def template_text = '''
<style>
        td {
            padding: 10px 0 10px 0;
        }
        .tableUnitRight {
            padding-left: 20px;
            max-width: 800px;
        }

        .tableUnitLeft {
            text-align: right;
        }

        .tableRow {
            border-bottom: 1px solid grey;
            vertical-align: top;
        }
</style>

<h3><b>''' + binding["mail_author"] + ''' - ''' + binding["mail_date"] + '''</b></h3>
<div><font size="3">''' + binding["mail_body"] + '''</font></div>
<br><br>
<table>
<tr class="tableRow">
<td class="tableUnitLeft">Код заявки:</td>
        <td class="tableUnitRight"><a href="http://support.trassir.com/browse/''' + binding["issue"] + '''">''' + binding["issue"] + '''</a></td>
</tr>
	<tr class="tableRow">
    	<td class="tableUnitLeft">Описание:</td>
<td class="tableUnitRight"><p>''' + binding["description"] + '''</p></td>
</tr>
    <tr class="tableRow">
		<td class="tableUnitLeft">Оборудование:</td>
<td class="tableUnitRight">''' + binding["devices"] + '''</td>
	</tr>
<tr class="tableRow">
<td class="tableUnitLeft">Контакты:</td>
		<td class="tableUnitRight">''' + binding["contacts"] + '''</td>
</tr>
    <tr class="tableRow">
		<td class="tableUnitLeft">Автор:</td>
<td class="tableUnitRight">''' + binding["author"] + '''</td>
	</tr>
<tr class="tableRow">
<td class="tableUnitLeft">E-mail автора:</td>
		<td class="tableUnitRight">''' + binding["author_mail"] + '''</td>
</tr>
    <tr class="tableRow">
		<td class="tableUnitLeft">Исполнитель:</td>
<td class="tableUnitRight">''' + binding["assignee"] + '''</td>
	</tr>
        <tr class="tableRow">
		<td class="tableUnitLeft">Вложения:</td>
<td class="tableUnitRight">''' + binding["attachment"] + '''</td>
	</tr>
</table>
<br>
<br>
<h3>История комментариев:</h3>
<br>
'''
        for (x in allComments) {
            template_text += "<u>" + x.getAuthorFullName() + " - " + x.getUpdated() + "</u><br>"
            template_text += formatText(x.getBody()) + "<br><br>"
        }

        return template_text
    }
}

SendToDepartment script = new SendToDepartment();


try {
    //script.run_test()
    config.compiled_template = script.run(issue);
    config.issue_summary = issue.getSummary();
} catch (Exception e) {
    def script_name = "send_to_department_template " + this.class.getName()
    def ex_string = "ERROR in script " + script_name + ": " +  e +
            "; issue: " + issue
    script.dl.send_error_email(ex_string)
    script.dl.write_comment_log("{color:#FF0000}" + ex_string + "{color}")
}