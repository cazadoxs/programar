package com.android.billingclient.api;
public final class ProductDetails { public java.util.List<SubscriptionOfferDetails> getSubscriptionOfferDetails(){return null;}
 public static final class SubscriptionOfferDetails { public String getBasePlanId(){return "";} public String getOfferToken(){return "";} public PricingPhases getPricingPhases(){return new PricingPhases();} }
 public static final class PricingPhases { public java.util.List<PricingPhase> getPricingPhaseList(){return java.util.Collections.emptyList();} }
 public static final class PricingPhase { public String getFormattedPrice(){return "";} public String getBillingPeriod(){return "";} public long getPriceAmountMicros(){return 0;} } }
