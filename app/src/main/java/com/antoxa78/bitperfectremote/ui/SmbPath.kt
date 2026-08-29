package com.antoxa78.bitperfectremote.ui

// A share root looks like: smb://user:pass@host/share/...
// Strip credentials when showing a label to avoid leaking it into the UI.
fun smbLabel(path: String): String {
    val withoutCreds = path.substringAfter("://").substringAfterLast('@')
    val share = withoutCreds.trimEnd('/').substringAfterLast('/')
    return share.ifBlank { withoutCreds }
}
