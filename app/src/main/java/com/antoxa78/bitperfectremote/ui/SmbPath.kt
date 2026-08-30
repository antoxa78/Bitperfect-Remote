package com.antoxa78.bitperfectremote.ui

// A share root looks like: smb://user:pass@host/share/...
// Strip credentials when showing a label to avoid leaking it into the UI.
fun smbLabel(path: String): String {
    val withoutCreds = path.substringAfter("://").substringAfterLast('@')
    val share = withoutCreds.trimEnd('/').substringAfterLast('/')
    return share.ifBlank { withoutCreds }
}

// Extract the host (hostname or IP) from an smb:// URI, stripping credentials
// and an optional port so the root network-shares list can show the server.
fun smbHost(path: String): String {
    val withoutCreds = path.substringAfter("://").substringAfterLast('@')
    return withoutCreds.substringBefore('/').substringBefore(':')
}
