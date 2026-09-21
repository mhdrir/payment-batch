package com.ascory.paymentbatch.csv;

/**
 * Thrown for problems that make a whole input file unusable (missing header, IO error,
 * structurally broken CSV). Single invalid records never trigger this exception.
 */
public class CsvReadException extends RuntimeException {

    public CsvReadException(String message) {
        super(message);
    }

    public CsvReadException(String message, Throwable cause) {
        super(message, cause);
    }
}

