package android.util

import java.util.regex.Pattern

/** The ready-made patterns of Android (an extension that checks a link or an address with them failed to load without the class) */
object Patterns {
    private const val LABEL = "[a-zA-Z0-9](?:[a-zA-Z0-9\\-]{0,61}[a-zA-Z0-9])?"

    @JvmField val IP_ADDRESS: Pattern = Pattern.compile("((25[0-5]|2[0-4][0-9]|[0-1][0-9]{2}|[1-9][0-9]|[1-9])\\.(25[0-5]|2[0-4][0-9]|[0-1][0-9]{2}|[1-9][0-9]|[1-9]|0)\\.(25[0-5]|2[0-4][0-9]|[0-1][0-9]{2}|[1-9][0-9]|[1-9]|0)\\.(25[0-5]|2[0-4][0-9]|[0-1][0-9]{2}|[1-9][0-9]|[0-9]))")

    @JvmField val TOP_LEVEL_DOMAIN: Pattern = Pattern.compile("[a-zA-Z]{2,63}")

    @JvmField val DOMAIN_NAME: Pattern = Pattern.compile("(($LABEL\\.)+[a-zA-Z]{2,63}|${IP_ADDRESS.pattern()})")

    @JvmField val WEB_URL: Pattern = Pattern.compile(
        "((?:(?i:http|https|rtsp)://(?:[a-zA-Z0-9\\$\\-_.+!*'(),;?&=%]{1,64}(?::[a-zA-Z0-9\\$\\-_.+!*'(),;?&=%]{1,25})?@)?)?" +
            "(?:${DOMAIN_NAME.pattern()})(?::\\d{1,5})?)" +
            "(/(?:(?:[a-zA-Z0-9;/?:@&=#~\\-.+!*'(),_\\$])|(?:%[a-fA-F0-9]{2}))*)?(?:\\b|$)",
    )

    @JvmField val EMAIL_ADDRESS: Pattern = Pattern.compile("[a-zA-Z0-9+._%\\-]{1,256}@[a-zA-Z0-9][a-zA-Z0-9\\-]{0,64}(\\.[a-zA-Z0-9][a-zA-Z0-9\\-]{0,25})+")

    @JvmField val PHONE: Pattern = Pattern.compile("(\\+[0-9]+[\\- .]*)?(\\([0-9]+\\)[\\- .]*)?([0-9][0-9\\- .]+[0-9])")
}
