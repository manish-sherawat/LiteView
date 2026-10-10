/*
 * LibreOfficeKitInit.h - Initialization entrypoint
 */

#ifndef INCLUDED_LIBREOFFICEKIT_LIBREOFFICEKITINIT_H
#define INCLUDED_LIBREOFFICEKIT_LIBREOFFICEKITINIT_H

#include "LibreOfficeKit.h"

#ifdef __cplusplus
extern "C" {
#endif

typedef LibreOfficeKit* (*lok_init_2_type)(const char* pInstallPath, const char* pUserProfileUrl);

static inline LibreOfficeKit* lok_init_2(const char* pInstallPath, const char* pUserProfileUrl) {
    extern LibreOfficeKit* libreofficekit_hook_2(const char* pInstallPath, const char* pUserProfileUrl);
    return libreofficekit_hook_2(pInstallPath, pUserProfileUrl);
}

#ifdef __cplusplus
}
#endif

#endif // INCLUDED_LIBREOFFICEKIT_LIBREOFFICEKITINIT_H
