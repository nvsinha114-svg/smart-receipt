package com.smartreceipt.service;

public enum LineContext {
    ITEM,
    ITEM_CONTINUATION,
    HEADER,
    ADDRESS,
    IDENTIFIER,
    TAX,
    DISCOUNT,
    SHIPPING,
    PAYMENT,
    TOTAL,
    SUBTOTAL,
    FOOTER,
    NOISE
}
