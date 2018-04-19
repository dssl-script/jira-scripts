import com.onresolve.scriptrunner.canned.jira.workflow.postfunctions.MailAttachment

def banned_ext(a) {
    def banned = [
            ".exe",
            ".dll"
    ]
    for (int i=0; i<banned.size();i++) {
        if (a.filename.toLowerCase().endsWith(banned[i])) {
            return true;
        }
    }
}


{MailAttachment a ->
    !banned_ext(a) && a.filesize < 5800**2}
