package com.nexus.feature.reader.pdf

import com.artifex.mupdf.fitz.Page
import com.artifex.mupdf.fitz.Quad

fun testSearch(page: Page, query: String): Array<kotlin.Array<Quad>>? {
    return page.search(query)
}
