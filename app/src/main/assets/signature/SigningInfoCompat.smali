# Kept separate from the legacy helper so pre-28 Android never loads SigningInfo types.
# AI-assisted contribution: OpenAI Codex.
.class public final Lapp/hexora/signature/SigningInfoCompat;
.super Ljava/lang/Object;
.field private static final self:Ljava/util/Map;

.method static constructor <clinit>()V
    .locals 1
    new-instance v0, Ljava/util/WeakHashMap;
    invoke-direct {v0}, Ljava/util/WeakHashMap;-><init>()V
    invoke-static {v0}, Ljava/util/Collections;->synchronizedMap(Ljava/util/Map;)Ljava/util/Map;
    move-result-object v0
    sput-object v0, Lapp/hexora/signature/SigningInfoCompat;->self:Ljava/util/Map;
    return-void
.end method

.method public static capture(Landroid/content/pm/PackageInfo;)Landroid/content/pm/SigningInfo;
    .locals 3
    iget-object v0, p0, Landroid/content/pm/PackageInfo;->signingInfo:Landroid/content/pm/SigningInfo;
    if-eqz v0, :done
    const-string v1, "@@PACKAGE@@"
    iget-object v2, p0, Landroid/content/pm/PackageInfo;->packageName:Ljava/lang/String;
    invoke-virtual {v1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z
    move-result v1
    if-eqz v1, :done
    sget-object v1, Lapp/hexora/signature/SigningInfoCompat;->self:Ljava/util/Map;
    sget-object v2, Ljava/lang/Boolean;->TRUE:Ljava/lang/Boolean;
    invoke-interface {v1, v0, v2}, Ljava/util/Map;->put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;
    :done
    return-object v0
.end method

.method private static isSelf(Landroid/content/pm/SigningInfo;)Z
    .locals 1
    sget-object v0, Lapp/hexora/signature/SigningInfoCompat;->self:Ljava/util/Map;
    invoke-interface {v0, p0}, Ljava/util/Map;->containsKey(Ljava/lang/Object;)Z
    move-result v0
    return v0
.end method

.method public static getApkContentsSigners(Landroid/content/pm/SigningInfo;)[Landroid/content/pm/Signature;
    .locals 1
    invoke-static {p0}, Lapp/hexora/signature/SigningInfoCompat;->isSelf(Landroid/content/pm/SigningInfo;)Z
    move-result v0
    if-eqz v0, :other
    invoke-static {}, Lapp/hexora/signature/OriginalCertificates;->current()[Landroid/content/pm/Signature;
    move-result-object v0
    return-object v0
    :other
    invoke-virtual {p0}, Landroid/content/pm/SigningInfo;->getApkContentsSigners()[Landroid/content/pm/Signature;
    move-result-object v0
    return-object v0
.end method

.method public static getSigningCertificateHistory(Landroid/content/pm/SigningInfo;)[Landroid/content/pm/Signature;
    .locals 1
    invoke-static {p0}, Lapp/hexora/signature/SigningInfoCompat;->isSelf(Landroid/content/pm/SigningInfo;)Z
    move-result v0
    if-eqz v0, :other
    @@HISTORY_RETURN@@
    :other
    invoke-virtual {p0}, Landroid/content/pm/SigningInfo;->getSigningCertificateHistory()[Landroid/content/pm/Signature;
    move-result-object v0
    return-object v0
.end method

.method public static hasMultipleSigners(Landroid/content/pm/SigningInfo;)Z
    .locals 1
    invoke-static {p0}, Lapp/hexora/signature/SigningInfoCompat;->isSelf(Landroid/content/pm/SigningInfo;)Z
    move-result v0
    if-eqz v0, :other
    const/4 v0, @@MULTIPLE@@
    return v0
    :other
    invoke-virtual {p0}, Landroid/content/pm/SigningInfo;->hasMultipleSigners()Z
    move-result v0
    return v0
.end method

.method public static hasPastSigningCertificates(Landroid/content/pm/SigningInfo;)Z
    .locals 1
    invoke-static {p0}, Lapp/hexora/signature/SigningInfoCompat;->isSelf(Landroid/content/pm/SigningInfo;)Z
    move-result v0
    if-eqz v0, :other
    const/4 v0, @@PAST@@
    return v0
    :other
    invoke-virtual {p0}, Landroid/content/pm/SigningInfo;->hasPastSigningCertificates()Z
    move-result v0
    return v0
.end method
