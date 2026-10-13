package com.linenexus.monitor.alarm;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.linenexus.monitor.config.AlarmProperties;

@Component
public class AlarmOfflineScheduler {

    private final AlarmEvaluateService evaluate;
    private final AlarmProperties props;

    public AlarmOfflineScheduler(AlarmEvaluateService evaluate, AlarmProperties props) {
        this.evaluate = evaluate;
        this.props = props;
    }

    @Scheduled(fixedDelayString = "${linenexus.alarm.offline-scan-ms:30000}")
    public void scan() {
        if (props.getOfflineScanMs() <= 0) {
            return;
        }
        evaluate.scanOffline();
    }
}
