package com.julflips.nerv_printer.utils;

/** Pure revision gate for an authoritative two-slot inventory exchange. */
public final class AuthoritativeInventorySwapGate {
    private AuthoritativeInventorySwapGate() {
    }

    public static boolean confirms(
        long submittedAfterRevision,
        long sourceRevision,
        long targetRevision,
        boolean exchanged
    ) {
        if (submittedAfterRevision < 0
            || sourceRevision < 0
            || targetRevision < 0) {
            throw new IllegalArgumentException(
                "Inventory revisions cannot be negative."
            );
        }
        return exchanged
            && sourceRevision > submittedAfterRevision
            && targetRevision > submittedAfterRevision;
    }
}
