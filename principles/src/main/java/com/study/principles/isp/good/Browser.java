package com.study.principles.isp.good;

/**
 * Aggregate for <b>implementers and the composition root</b>: "a browser is all of these". Clients should
 * still ask for the narrowest role — a client taking {@code Browser} is the fat interface again.
 */
public interface Browser extends Navigation, Interaction, Screenshots, Cookies {
}
