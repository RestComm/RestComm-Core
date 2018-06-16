package org.restcomm.connect.commons.dao;

/**
 * @author mariafarooq
 *
 */
public enum MessageError {

    QUEUE_OVERFLOW (30001, "Queue overflow"),

    ACCOUNT_SUSPENDED (30002, "Account suspended"),

    UNREACHABLE_DESTINATION_HANDSET (30003, "Unreachable destination handset"),

    MESSAGE_BLOCKED (30004, "Message blocked"),

    UNKNOWN_DESTINATION_HANDSET (30005, "Unknown destination handset"),

    LANDLINE_OR_UNREACHABLE_CARRIER (30006, "Landline or unreachable carrier"),

    CARRIER_VIOLATION (30007, "Carrier violation"),

    UNKNOWN_ERROR (30008, "Unknown error"),

    MISSING_SEGMENT (30009, "Missing segment"),

    MESSAGE_PRICE_EXCEEDS_MAX_PRICE (30010, "Message price exceeds max price.");

    private final Integer errorCode;
    private final String errorMessage;

    private MessageError(final Integer errorCode, final String errorMessage) {
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
    }

    public Integer getErrorCode() {
        return errorCode;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public static MessageError getErrorValue(final Integer errorCode) {
        final MessageError[] values = values();
        for (final MessageError value : values) {
            if (value.getErrorCode().equals(errorCode)) {
                return value;
            }
        }
        throw new IllegalArgumentException(errorCode + " is not a valid errorCode.");
    }
}
