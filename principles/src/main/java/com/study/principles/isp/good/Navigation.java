package com.study.principles.isp.good;

/** Role: moving between URLs. Web only — native apps have screens, not addresses. */
public interface Navigation {

    void open(String url);

    String currentUrl();
}
