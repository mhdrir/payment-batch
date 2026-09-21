package com.ascory.paymentbatch.csv;

import java.util.List;

/**
 * Column names of the input CSV as well as the additional result columns.
 */
public final class CsvColumns {

    public static final String PAYMENT_ID = "payment_id";
    public static final String RECIPIENT_NAME = "recipient_name";
    public static final String RECIPIENT_IBAN = "recipient_iban";
    public static final String AMOUNT = "amount";
    public static final String CURRENCY = "currency";
    public static final String PAYMENT_REFERENCE = "payment_reference";

    public static final String STATUS = "status";
    public static final String PROCESSING_TYPE = "processing_type";
    public static final String MESSAGE = "message";

    /** Columns that must be present in the input file. */
    public static final List<String> REQUIRED_INPUT_COLUMNS =
            List.of(PAYMENT_ID, RECIPIENT_NAME, RECIPIENT_IBAN, AMOUNT, CURRENCY, PAYMENT_REFERENCE);

    /** Result file layout: original payment data plus status, processing type and message. */
    public static final String[] RESULT_COLUMNS = {
            PAYMENT_ID, RECIPIENT_NAME, RECIPIENT_IBAN, AMOUNT, CURRENCY, PAYMENT_REFERENCE,
            STATUS, PROCESSING_TYPE, MESSAGE
    };

    private CsvColumns() {
    }
}

