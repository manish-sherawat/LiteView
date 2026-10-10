/*
 * This file provides the LibreOfficeKit C API definitions.
 * Based on LibreOfficeKit / LOKit standard public interface.
 */

#ifndef INCLUDED_LIBREOFFICEKIT_LIBREOFFICEKIT_H
#define INCLUDED_LIBREOFFICEKIT_LIBREOFFICEKIT_H

#include <stddef.h>
#include <stdint.h>
#include <stdbool.h>

#ifdef __cplusplus
extern "C" {
#endif

typedef struct _LibreOfficeKit LibreOfficeKit;
typedef struct _LibreOfficeKitClass LibreOfficeKitClass;

typedef struct _LibreOfficeKitDocument LibreOfficeKitDocument;
typedef struct _LibreOfficeKitDocumentClass LibreOfficeKitDocumentClass;

typedef enum {
    LOK_DOCTYPE_TEXT,
    LOK_DOCTYPE_SPREADSHEET,
    LOK_DOCTYPE_PRESENTATION,
    LOK_DOCTYPE_DRAWING,
    LOK_DOCTYPE_OTHER
} LibreOfficeKitDocumentType;

typedef enum {
    LOK_TILEMODE_RGBA,
    LOK_TILEMODE_BGRA
} LibreOfficeKitTileMode;

struct _LibreOfficeKit {
    LibreOfficeKitClass* pClass;
};

struct _LibreOfficeKitClass {
    size_t nSize;

    void (*destroy)(LibreOfficeKit* pThis);

    LibreOfficeKitDocument* (*documentLoad)(
        LibreOfficeKit* pThis,
        const char* pURL
    );

    char* (*getError)(LibreOfficeKit* pThis);

    void (*freeError)(char* pFree);

    LibreOfficeKitDocument* (*documentLoadWithOptions)(
        LibreOfficeKit* pThis,
        const char* pURL,
        const char* pOptions
    );
};

struct _LibreOfficeKitDocument {
    LibreOfficeKitDocumentClass* pClass;
};

struct _LibreOfficeKitDocumentClass {
    size_t nSize;

    void (*destroy)(LibreOfficeKitDocument* pThis);

    int (*saveAs)(
        LibreOfficeKitDocument* pThis,
        const char* pUrl,
        const char* pFormat,
        const char* pFilterOptions
    );

    int (*getDocumentType)(LibreOfficeKitDocument* pThis);

    int (*getParts)(LibreOfficeKitDocument* pThis);

    int (*getPart)(LibreOfficeKitDocument* pThis);

    void (*setPart)(
        LibreOfficeKitDocument* pThis,
        int nPart
    );

    char* (*getPartName)(
        LibreOfficeKitDocument* pThis,
        int nPart
    );

    void (*getDocumentSize)(
        LibreOfficeKitDocument* pThis,
        long* pWidth,
        long* pHeight
    );

    void (*paintTile)(
        LibreOfficeKitDocument* pThis,
        unsigned char* pBuffer,
        const int nCanvasWidth,
        const int nCanvasHeight,
        const int nTilePosX,
        const int nTilePosY,
        const int nTileWidth,
        const int nTileHeight
    );

    int (*getTileMode)(LibreOfficeKitDocument* pThis);

    void (*setDocumentPassword)(
        LibreOfficeKitDocument* pThis,
        const char* pURL,
        const char* pPassword
    );
};

#ifdef __cplusplus
}
#endif

#endif // INCLUDED_LIBREOFFICEKIT_LIBREOFFICEKIT_H
