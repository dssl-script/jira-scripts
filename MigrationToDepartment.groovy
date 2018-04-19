import com.atlassian.jira.component.ComponentAccessor
import com.atlassian.jira.event.type.EventDispatchOption
import com.atlassian.jira.issue.Issue
import com.atlassian.jira.issue.IssueManager
import com.atlassian.jira.issue.MutableIssue
import com.atlassian.jira.issue.UpdateIssueRequest
import com.atlassian.jira.issue.comments.Comment
import com.atlassian.jira.issue.comments.CommentManager
import com.atlassian.jira.issue.customfields.option.Options
import com.atlassian.jira.user.ApplicationUser
import org.apache.log4j.Level
import org.apache.log4j.Logger
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import com.atlassian.jira.issue.fields.CustomField;
import com.onresolve.scriptrunner.canned.jira.workflow.postfunctions.SendCustomEmail

class DebugLog {
    Logger log;

    DebugLog() {
        log = Logger.getLogger("com.acme.CreateSubtask");
        log.setLevel(Level.DEBUG);
    }

    public void debug(text) {
        log.debug(text)
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

class TemplateBuilder {

    CommentManager commentMgr = ComponentAccessor.getCommentManager();

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

    def compile(issue) {

        def allComments = commentMgr.getComments(issue)
        def last_comment = null
        if (allComments.size() > 0) {
            last_comment = allComments[allComments.size()-1];
        } else {
            throw new Exception("No comments");
        }

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


class MigrationToDepartment {

    public DebugLog dbg;
    public String mail_recievers;
    public String mail_subject;
    public DebugIssueLogger dl;

    public ApplicationUser update_user = ComponentAccessor.getUserManager().getUserByName("v.agafonov")

    def mail_recievers = [
            "v.monakhov@dssl.ru",
    ]


    MigrationToDepartment() {
        IssueManager issueManager = ComponentAccessor.getIssueManager();
        Issue debug_issue = issueManager.getIssueObject("SCR-1702")
        ApplicationUser debug_user = ComponentAccessor.getUserManager().getUserByName("v.monakhov")

        dbg = new DebugLog();
        dl = new DebugIssueLogger(debug_issue, debug_user)
        dl.email_recievers = mail_recievers
    };

    private String prepare_mail(issue) {

        TemplateBuilder template = new TemplateBuilder();

        String text = template.compile(issue)
        //dbg.debug(text)

        return text
    }

    private String change_resolution(issue, String dep_name) {
        String log_text = "Resolution change started\n"
        def resol = ComponentAccessor.getCustomFieldManager().getCustomFieldObject("customfield_11300")
        Options options = ComponentAccessor.getOptionsManager().getOptions(resol.getConfigurationSchemes().listIterator().next().getOneAndOnlyConfig());
        //options[6].getOptionId()
        log_text = "Dep_name: " + dep_name + "\n";
        if (dep_name == "service") {
            if (options[5].getOptionId() != 11617) {
                dl.write_comment_log("{color:#FF0000}" + "Issue resolution search error (" + issue + ") " +
                        ": options[5] " + options[5].getOptionId() + " " + options[5].getValue() + " not 'to service': " + options + "{color}")
                throw new IllegalArgumentException ("Issue resolution search error (" + issue + ") " +
                        ": options[5] " + options[5].getOptionId() + " " + options[5].getValue() + " not 'to service': " + options);
            }
            log_text = "Resolution set: " + options[5].getOptionId() + " " + options[5].getValue() + "\n"
            issue.setCustomFieldValue(resol, options[5])
        } else if (dep_name == "commerce") {
            if (options[6].getOptionId() != 11618) {
                dl.write_comment_log("{color:#FF0000}" + "Issue resolution search error (" + issue + ") " +
                        ": options[6] " + options[6].getOptionId() + " " + options[6].getValue() + " not 'to commerce': " + options + "{color}")
                throw new IllegalArgumentException ("Issue resolution search error (" + issue + ") " +
                        ": options[6] " + options[6].getOptionId() + " " + options[6].getValue() + " not 'to commerce': " + options);
            }
            log_text = "Resolution set: " + options[6].getOptionId() + " " + options[6].getValue() + "\n"
            issue.setCustomFieldValue(resol, options[6])
        }
        IssueUpdater.updateIssue(issue, update_user)
        return log_text
    }

    public String run(issue) {
        String log_text = "Started, issue: " + issue + "\n"
        String rec_com = ""
        String rec_serv = ""
        String department = ComponentAccessor.getCustomFieldManager().getCustomFieldObject("customfield_12019").getValue(issue);
        log_text += "Department: " + department + "\n"

        try {
            CustomField region = ComponentAccessor.getCustomFieldManager().getCustomFieldObject("customfield_12017");
            Pattern p = Pattern.compile("<(.*)><(.*)>");

            Matcher m = p.matcher(region.getValue(issue).toString());

            m.find();
            if (m.groupCount() == 2) {
                rec_com = m.group(1);www
                rec_serv = m.group(2);
            } else {
                dl.write_comment_log("{color:#FF0000}" + "Issue (" + issue + ") No match with pattern" + "{color}")
                throw new NullPointerException("No match");
            }
        } catch (Exception e) {
            rec_com = "s.poluhin@dssl.ru"
            rec_serv = "l.zvezdova@dssl.ru"
            log_text += "{color:#FF0000}ERROR: " + e + "\nRecievers to default: " + rec_com + ";" + rec_serv + "{color}\n";
            //dbg.debug("ERROR: " + e);
        }

        department = department.toString()
        def mail_text = ""
        String resolution_res = "";


        if (department == "Сервисный центр") {
            mail_text = prepare_mail(issue)

            mail_subject = "Перевод в сервисный центр";
            mail_recievers = rec_serv;
            resolution_res = change_resolution(issue, "service")
        } else if (department == "Коммерческий отдел") {
            mail_text = prepare_mail(issue)
            mail_subject = "Перевод в коммерческий отдел"
            mail_recievers = rec_com;
            resolution_res = change_resolution(issue, "commerce")
        } else {
            dl.write_comment_log("{color:#FF0000}" + "Issue (" + issue + ") " + " incorrect department " +
                    "\nERROR: unknown value - " + department + "{color}")
            throw new Exception("ERROR: unknown value - " + department);
        }
        mail_subject += " - " + issue.getSummary()
        log_text += resolution_res
        log_text += "Mail subject: " + mail_subject + "\n";
        log_text += "Mail recievers: " + mail_recievers;
        //dbg.debug(mail_subject + " " + mail_recievers);
        dl.write_comment_log(log_text)

        return mail_text
    }

    public void run_tests() {
        IssueManager issueManager = ComponentAccessor.getIssueManager();
        Issue debug_issue = issueManager.getIssueObject("JST-2")
        ApplicationUser debug_user = ComponentAccessor.getUserManager().getUserByName("v.monakhov")
        run(debug_issue)
    }
}


MigrationToDepartment script = new MigrationToDepartment();


try {
    //script.run_tests();
    config.template = script.run(issue)
    //script.mail_recievers = "1202@dssl.ru"
    mail.setTo(script.mail_recievers)
    mail.setSubject(script.mail_subject)
    mail.setCc("1202@dssl.ru")
} catch (Exception e) {
    def script_name = "MigrationToDepartment " + this.class.getName()
    def ex_string = "ERROR in script " + script_name + ": " +  e +
            "; issue: " + issue
    script.dl.send_error_email("<font color=\"red\">" + ex_string + "</font>")
    script.dl.write_comment_log("{color:#FF0000}" + ex_string + "{color}")
}

