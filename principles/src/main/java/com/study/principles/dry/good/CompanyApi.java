package com.study.principles.dry.good;

import com.study.principles.dry.FakeServer;
import com.study.principles.dry.HttpResponse;
import com.study.principles.dry.Release;

import java.util.Map;

/** A separate client for a separate endpoint — they only looked alike. */
public final class CompanyApi {

    private final FakeServer http;

    public CompanyApi(FakeServer http) {
        this.http = http;
    }

    public HttpResponse create(Map<String, ?> company) {
        return http.post(Release.COMPANIES_PATH, company);
    }
}
