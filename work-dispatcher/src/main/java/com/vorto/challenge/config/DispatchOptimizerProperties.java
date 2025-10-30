package com.vorto.challenge.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "dispatch.optimizer")
public class DispatchOptimizerProperties {

    private boolean enabled;
    private int knnK1;
    private int knnK2;
    private int advisoryKey = 884422;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getKnnK1() {
        return knnK1;
    }

    public void setKnnK1(int knnK1) {
        this.knnK1 = knnK1;
    }

    public int getKnnK2() {
        return knnK2;
    }

    public void setKnnK2(int knnK2) {
        this.knnK2 = knnK2;
    }

    public int getAdvisoryKey() {
        return advisoryKey;
    }

    public void setAdvisoryKey(int advisoryKey) {
        this.advisoryKey = advisoryKey;
    }
}

