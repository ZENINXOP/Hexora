# Hexora signature compatibility helper. AI-assisted contribution: OpenAI Codex.
.class public final Lapp/hexora/signature/OriginalCertificates;
.super Ljava/lang/Object;

.method public static getSignatures(Landroid/content/pm/PackageInfo;)[Landroid/content/pm/Signature;
    .locals 2
    const-string v0, "@@PACKAGE@@"
    iget-object v1, p0, Landroid/content/pm/PackageInfo;->packageName:Ljava/lang/String;
    invoke-virtual {v0, v1}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z
    move-result v0
    if-eqz v0, :other
    # Preserve the API's null result when GET_SIGNATURES was not requested.
    iget-object v0, p0, Landroid/content/pm/PackageInfo;->signatures:[Landroid/content/pm/Signature;
    if-eqz v0, :other
    invoke-static {}, Lapp/hexora/signature/OriginalCertificates;->legacy()[Landroid/content/pm/Signature;
    move-result-object v0
    return-object v0
    :other
    iget-object v0, p0, Landroid/content/pm/PackageInfo;->signatures:[Landroid/content/pm/Signature;
    return-object v0
.end method

.method public static current()[Landroid/content/pm/Signature;
    .locals 4
    @@CURRENT@@
.end method

.method public static history()[Landroid/content/pm/Signature;
    .locals 4
    @@HISTORY@@
.end method

.method public static legacy()[Landroid/content/pm/Signature;
    .locals 4
    @@LEGACY@@
.end method
