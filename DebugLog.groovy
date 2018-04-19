import org.apache.log4j.Level
import org.apache.log4j.Logger

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
