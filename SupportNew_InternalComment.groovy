import com.atlassian.jira.component.ComponentAccessor
import com.atlassian.jira.issue.comments.CommentManager
import com.atlassian.jira.util.json.JSONObject

def run(issue) {
    final SD_PUBLIC_COMMENT = "sd.public.comment"
    def internal_comment_field = ComponentAccessor.getCustomFieldManager().getCustomFieldObject("customfield_12021");
    def comment_text = internal_comment_field.getValue(issue);
    issue.setCustomFieldValue(internal_comment_field, null);
    CommentManager commentMgr = ComponentAccessor.getCommentManager();
    def properties = [(SD_PUBLIC_COMMENT): new JSONObject(["internal": true])];
    commentMgr.create(issue, issue.getAssigneeUser(), comment_text, null, null, new Date(), properties, false);
}

run(issue)