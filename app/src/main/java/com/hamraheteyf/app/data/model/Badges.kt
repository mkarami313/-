package com.hamraheteyf.app.data.model

/**
 * Derives the public [AuthorBadge] for a user.
 *
 * This is the single source of truth for the "تخصص تأییدشده" badge. The verified badge
 * is granted ONLY when an admin has set [VerificationStatus.VERIFIED]; a user cannot
 * produce a verified badge by choosing the professional role.
 */
fun resolveAuthorBadge(
    accountType: AccountType,
    verificationStatus: VerificationStatus?,
): AuthorBadge = when (accountType) {
    AccountType.FAMILY -> AuthorBadge.FAMILY
    AccountType.COMMUNITY -> AuthorBadge.COMMUNITY
    AccountType.PROFESSIONAL,
    AccountType.EDUCATOR,
    AccountType.SOCIAL_WORKER ->
        if (verificationStatus == VerificationStatus.VERIFIED)
            AuthorBadge.PROFESSIONAL_VERIFIED
        else
            AuthorBadge.PROFESSIONAL_UNVERIFIED
}

/** True for the account types that may submit a professional verification request. */
fun AccountType.canRequestVerification(): Boolean = when (this) {
    AccountType.PROFESSIONAL, AccountType.EDUCATOR, AccountType.SOCIAL_WORKER -> true
    AccountType.FAMILY, AccountType.COMMUNITY -> false
}
