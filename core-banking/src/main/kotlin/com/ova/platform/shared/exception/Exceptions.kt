package com.ova.platform.shared.exception

import org.springframework.http.HttpStatus

open class OvaException(
    val status: HttpStatus,
    override val message: String,
    val errorCode: String
) : RuntimeException(message)

class NotFoundException(resource: String, id: String) :
    OvaException(HttpStatus.NOT_FOUND, "$resource not found: $id", "NOT_FOUND")

class ConflictException(message: String) :
    OvaException(HttpStatus.CONFLICT, message, "CONFLICT")

class BadRequestException(message: String) :
    OvaException(HttpStatus.BAD_REQUEST, message, "BAD_REQUEST")

class ForbiddenException(message: String) :
    OvaException(HttpStatus.FORBIDDEN, message, "FORBIDDEN")

class UnauthorizedException(message: String) :
    OvaException(HttpStatus.UNAUTHORIZED, message, "UNAUTHORIZED")

class InsufficientFundsException :
    OvaException(HttpStatus.UNPROCESSABLE_ENTITY, "Insufficient funds", "INSUFFICIENT_FUNDS")

class InsufficientBalanceException(message: String) :
    OvaException(HttpStatus.UNPROCESSABLE_ENTITY, message, "INSUFFICIENT_BALANCE")

class ComplianceRejectedException(reason: String) :
    OvaException(HttpStatus.UNPROCESSABLE_ENTITY, "Compliance check failed: $reason", "COMPLIANCE_REJECTED")

class QuoteExpiredException :
    OvaException(HttpStatus.GONE, "FX quote has expired", "QUOTE_EXPIRED")
