package com.android.billingclient.api;
public final class AcknowledgePurchaseParams { public static Builder newBuilder(){return new Builder();}
 public static final class Builder { public Builder setPurchaseToken(String t){return this;} public AcknowledgePurchaseParams build(){return new AcknowledgePurchaseParams();} } }
