package com.android.billingclient.api;
public final class Purchase { public java.util.List<String> getProducts(){return java.util.Collections.emptyList();} public int getPurchaseState(){return 0;} public boolean isAcknowledged(){return true;} public String getPurchaseToken(){return "";}
 public static final class PurchaseState { public static final int UNSPECIFIED_STATE=0, PURCHASED=1, PENDING=2; } }
