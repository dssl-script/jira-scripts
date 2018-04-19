<style>
.MyTab {text-indent: 2em;}
</style>

<h3>$allComments.getLastComment($issue).getAuthorFullName() $allComments.getLastComment($issue).getUpdated() </h3>
<div><font size="3">$allComments.getLastComment($issue).getBody()</font></div>
<br><br>
<table>
<tr>
<td>Issue key:</td>
        <td class="MyTab"><a href="http://support.trassir.com/browse/$issue.getKey()">$issue.getKey()</a></td>
</tr>
</table>
#foreach ($comment in $allComments.getComments($issue))
<u>$comment.getAuthorFullName() $comment.getUpdated()</u><br>
    $comment.getBody()<br>
    <br>
#end
