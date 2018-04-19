import com.atlassian.jira.component.ComponentAccessor
import com.atlassian.jira.event.type.EventDispatchOption
import com.atlassian.jira.issue.Issue
import com.atlassian.jira.issue.IssueManager
import com.atlassian.jira.issue.MutableIssue
import com.atlassian.jira.issue.UpdateIssueRequest
import com.atlassian.jira.issue.comments.Comment
import com.atlassian.jira.issue.comments.CommentManager
import com.atlassian.jira.user.ApplicationUser
import org.apache.log4j.Level
import org.apache.log4j.Logger
import java.text.SimpleDateFormat
import com.onresolve.scriptrunner.canned.jira.workflow.postfunctions.SendCustomEmail


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

class WorkShedule {
    final static String shedule_path = "/var/atlassian/application-data/jira/scripts/work_shedule.txt";

    private static ArrayList get_flines(path) {
        BufferedReader br = new BufferedReader(new FileReader(path));
        ArrayList lines = new ArrayList();
        try {
            String line = br.readLine();
            while (line != null)
            {
                line = br.readLine();
                lines.add(line)
            }
        } finally {
            br.close();
        }
        return lines
    }

    public static boolean is_working_day(String check_date) {
        def data = get_flines(shedule_path)
        for (String line : data) {
            if (line) {
                def vals = line.split("\t")
                def day_type = vals[0];
                def date = vals[1];
                if (date == check_date) {
                    if (day_type == "в") {
                        return false;
                    } else if (day_type == "р") {
                        return true;
                    }
                }
            }
        }
    }
}

class IncreaseNextStepDate {
    DebugLog dbg;
    IssueManager issueManager = ComponentAccessor.getIssueManager();
    Issue debug_issue = issueManager.getIssueObject("SCR-1737")
    ApplicationUser debug_user = ComponentAccessor.getUserManager().getUserByName("v.monakhov")
    ApplicationUser update_user = ComponentAccessor.getUserManager().getUserByName("v.agafonov")

    SimpleDateFormat dateFormat = new SimpleDateFormat("dd,MM,yyyy");
    def log_text = ""

    public DebugIssueLogger dl;

    IncreaseNextStepDate() {
        dbg = new DebugLog();
        dl = new DebugIssueLogger(debug_issue, debug_user)
        dl.email_recievers = [
                "v.monakhov@dssl.ru",
        ]
    }

    def get_work_date(check_date) {
        def start_date = check_date
        def max_increase_tries = 20
        def cur_try = 0
        def date_string = dateFormat.format(check_date.getTime());
        def is_working = WorkShedule.is_working_day(date_string)
        log_text += "Try " + date_string + "...\n"
        log_text += "Is working: " + is_working + "\n"
        if (!is_working) {
            while (!is_working) {
                check_date.add(Calendar.DAY_OF_MONTH, 1)
                date_string = dateFormat.format(check_date.getTime());
                is_working = WorkShedule.is_working_day(date_string)
                if (!is_working)
                    log_text += date_string + " no work, increase...(tries: " + cur_try + ")\n"
                else if (is_working) {
                    log_text += date_string + " is work day, stop.\n"
                    return check_date
                }
                if (cur_try > max_increase_tries) {
                    log_text += "Too much tries, exception\n"
                    throw new Exception("Too much tries to find work day! Start date: " + dateFormat.format(start_date.getTime())
                                        + "; Last date: " + dateFormat.format(check_date.getTime())
                                        )
                }
                cur_try += 1
            }
        } else if (is_working) {
            return check_date
        }
        return
    }

    def change_date(issue, start_date, debug = false) {
        log_text = "\n--------------------\nIssue: " + issue + "\n"
        def NextStepDate_field = ComponentAccessor.getCustomFieldManager().getCustomFieldObject("customfield_10809")
        def Prolong_field = ComponentAccessor.getCustomFieldManager().getCustomFieldObject("customfield_12024")
        def prolong_time
        prolong_time = Prolong_field.getValue(issue).toString()

        dateFormat.setTimeZone(start_date.getTimeZone());

        log_text += "Date: " + dateFormat.format(start_date.getTime()) + "\nSelected period: " + prolong_time + "\n"
        if (prolong_time == "null") {
            log_text += "Is null, break\n"
            return
        } else if (prolong_time == "На день") {
            start_date.add(Calendar.DAY_OF_MONTH, 1)
            start_date = get_work_date(start_date)
        } else if (prolong_time == "На час") {
            start_date.add(Calendar.HOUR, 1)
        } else if (prolong_time == "На неделю") {
            start_date.add(Calendar.WEEK_OF_MONTH, 1)
            start_date = get_work_date(start_date)
        } else {
            throw new Exception("Unknown prolong_time: " + prolong_time);
        }
        def new_date = start_date.getTime()
        if (debug == false) {
            issue.setCustomFieldValue(NextStepDate_field, new_date.toTimestamp())
            issue.setCustomFieldValue(Prolong_field, null)
            IssueUpdater.updateIssue(issue, update_user)
        } else {
            log_text += "NextStepDate: " + dateFormat.format(new_date.getTime())
        }
    }

    def run(issue) {
        Calendar cal = new GregorianCalendar();
        change_date(issue, cal)
        dbg.debug(log_text)
        dl.write_comment_log(log_text)
    }

    def run_test() {
        Calendar cal = new GregorianCalendar();

        Issue issue = issueManager.getIssueObject("JST-2")

        cal.setTimeInMillis(1525332586000) //03.05.18
        change_date(issue, cal, true)
        dbg.debug(log_text)
        dl.write_comment_log(log_text)

        cal.setTimeInMillis(1525246186000) //02.05.18
        change_date(issue, cal, true)
        dbg.debug(log_text)

        cal.setTimeInMillis(1524986986000) //29.04.18
        change_date(issue, cal, true)
        dbg.debug(log_text)

        cal.setTimeInMillis(1524468586000) //23.04.18
        change_date(issue, cal, true)
        dbg.debug(log_text)
    }
}


IncreaseNextStepDate script = new IncreaseNextStepDate();

try {
    script.run(issue)
    //script.run_test()
} catch (Exception e) {
    def script_name = "IncreaseNextStepDate " + this.class.getName()
    def ex_string = "ERROR in script " + script_name + ": " +  e +
            "; issue: " + issue
    script.dl.send_error_email("<font color=\"red\">" + ex_string + "</font>")
    script.dl.write_comment_log("{color:#FF0000}" + ex_string + "{color}")
}