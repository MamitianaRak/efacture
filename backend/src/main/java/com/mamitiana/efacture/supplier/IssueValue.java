package com.mamitiana.efacture.supplier;

import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class IssueValue {

    private String code;

    @Enumerated(EnumType.STRING)
    private InvoiceChecks.Severity severity;

    private String message;
}
