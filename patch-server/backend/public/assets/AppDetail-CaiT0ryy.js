import{_ as kl,r as y,a as le,z as H,A as Vl,o as xl,c as S,m as x,b as c,d as n,f as l,w as t,g as _,e as C,t as f,i as V,B as Ul,E as u,u as Sl,q as Cl,j as r,k as q,C as ql,l as Il,D as be,F as re,h as $e,G as Re,H as E,v as Al,I as zl,J as Pl,K as jl,L as $l,y as ie}from"./index-BnZprTwO.js";const Rl={key:0,class:"app-detail-container"},Dl={class:"app-header"},Ol={class:"app-title"},Kl={class:"app-icon"},Fl=["src"],Tl={key:0,class:"patches-list"},El={class:"patch-info"},Bl={class:"patch-version"},Nl={class:"patch-details"},Jl={class:"patch-desc"},Ml={class:"patch-meta"},Ll={class:"patch-actions"},Hl={class:"versions-section"},hl={class:"section-header"},Gl={key:0,class:"versions-list"},Yl={class:"version-info"},Wl={class:"version-header"},Ql={key:1,style:{color:"#f56c6c","font-size":"13px","margin-left":"4px"}},Xl={class:"version-details"},Zl={class:"version-desc"},et={key:0,class:"version-changelog"},lt={class:"version-meta"},tt={key:0},at={class:"version-actions"},ot={class:"api-docs"},nt={class:"api-section"},st={class:"copy-field"},rt={class:"copy-field"},it={class:"api-section"},dt={class:"api-block"},ut={class:"api-header"},pt={class:"code-block"},ft={class:"api-section"},ct={class:"api-block"},vt={class:"api-header"},mt={class:"code-block"},_t={class:"api-section"},gt={class:"api-block"},yt={class:"api-header"},bt={class:"code-block"},wt={class:"api-section"},kt={class:"code-actions"},Vt={class:"code-block"},xt={class:"code-actions"},Ut={class:"code-block"},St={class:"generate-section"},Ct={style:{"line-height":"1.8"}},qt={style:{margin:"12px 0 0 0"}},It={style:{margin:"8px 0 0 0"}},At={key:0},zt={key:1,style:{color:"#e6a23c"}},Pt={key:2,style:{color:"#909399"}},jt={key:0,style:{"margin-top":"8px","font-size":"13px",color:"#67c23a"}},$t={style:{"text-align":"center","margin-top":"8px","font-size":"14px",color:"#666"}},Rt={style:{color:"#d4af7a","font-size":"18px"}},Dt={style:{margin:"0","font-size":"13px"}},Ot={__name:"AppDetail",setup(Kt){const A=Ul(),we=Sl(),s=y(null),h=y("patches"),G=y(!1),de=y(!1),De=y(null),te=y([]),ue=y(!1),Y=y(!1),W=y(!1),ae=y(!1),pe=y(null),v=le({versionName:"",versionCode:null,description:"",changelog:"",downloadUrl:"",isForceUpdate:!1,minSupportedVersion:"",file:null}),b=le({id:null,version_name:"",version_code:null,description:"",changelog:"",download_url:"",is_force_update:0,min_supported_version:"",status:"active"}),U=le({version:"",base_version:"",description:"",force_update:!1,file:null}),w=le({version:"",base_version:"",description:"",force_update:!1,baseApk:null,newApk:null}),Q=y(!1),P=y(0),fe=y(null),ce=y(null),B=y(!1),ve=y(!1),Oe=y(null),ke=y([]),oe=y(null),X=y(!1),me=y(!1),k=le({patchId:null,version:"",percentage:100,status:"active",forceUpdate:!1}),z=y(""),$=y(null),_e=y(!1),j=H(()=>window.location.origin),Ke=[{name:"app_id",type:"string",required:!0,desc:"应用的唯一标识"},{name:"version",type:"string",required:!0,desc:"当前应用版本号"},{name:"device_id",type:"string",required:!0,desc:"设备唯一标识（用于灰度发布）"}],Fe=[{name:"app_id",type:"string",required:!0,desc:"应用的唯一标识"},{name:"patch_id",type:"string",required:!0,desc:"补丁 ID"},{name:"device_id",type:"string",required:!0,desc:"设备唯一标识"},{name:"success",type:"boolean",required:!0,desc:"应用是否成功"},{name:"error_message",type:"string",required:!1,desc:"失败时的错误信息"}],Ve=H(()=>{var o,e;return`GET ${j.value}/api/client/check-update?app_id=${((o=s.value)==null?void 0:o.app_id)||"YOUR_APP_ID"}&version=1.0.0&device_id=device123

// 使用 OkHttp
val url = "${j.value}/api/client/check-update" +
    "?app_id=${((e=s.value)==null?void 0:e.app_id)||"YOUR_APP_ID"}" +
    "&version=1.0.0" +
    "&device_id=\${getDeviceId()}"

val request = Request.Builder()
    .url(url)
    .get()
    .build()

client.newCall(request).execute()`}),Te=`{
  "hasUpdate": true,
  "patch": {
    "id": 1,
    "version": "1.0.1",
    "patch_id": "patch_123456",
    "base_version": "1.0.0",
    "file_size": 1048576,
    "md5": "abc123def456...",
    "download_url": "${j.value}/downloads/patch-123456.zip",
    "force_update": false,
    "description": "修复了一些问题"
  }
}`,Ee=H(()=>`// 下载补丁文件
val downloadUrl = patchInfo.download_url
val file = File(context.cacheDir, "patch.zip")

val request = Request.Builder()
    .url(downloadUrl)
    .get()
    .build()

client.newCall(request).execute().use { response ->
    if (response.isSuccessful) {
        response.body?.byteStream()?.use { input ->
            file.outputStream().use { output ->
                input.copyTo(output)
            }
        }
    }
}`),xe=H(()=>{var o,e;return`POST ${j.value}/api/client/report
Content-Type: application/json

{
  "app_id": "${((o=s.value)==null?void 0:o.app_id)||"YOUR_APP_ID"}",
  "patch_id": "patch_123456",
  "device_id": "device123",
  "success": true,
  "error_message": null
}

// 使用 OkHttp
val json = JSONObject().apply {
    put("app_id", "${((e=s.value)==null?void 0:e.app_id)||"YOUR_APP_ID"}")
    put("patch_id", patchId)
    put("device_id", getDeviceId())
    put("success", true)
}

val body = json.toString()
    .toRequestBody("application/json".toMediaType())

val request = Request.Builder()
    .url("${j.value}/api/client/report")
    .post(body)
    .build()

client.newCall(request).execute()`}),Ue=H(()=>{var o;return`class PatchManager(private val context: Context) {
    private val client = OkHttpClient()
    private val appId = "${((o=s.value)==null?void 0:o.app_id)||"YOUR_APP_ID"}"
    private val baseUrl = "${j.value}"
    
    // 检查更新
    suspend fun checkUpdate(currentVersion: String): PatchInfo? {
        val deviceId = getDeviceId()
        val url = "$baseUrl/api/client/check-update" +
            "?app_id=$appId" +
            "&version=$currentVersion" +
            "&device_id=$deviceId"
        
        val request = Request.Builder()
            .url(url)
            .get()
            .build()
        
        return withContext(Dispatchers.IO) {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val json = JSONObject(response.body?.string() ?: "")
                    if (json.getBoolean("hasUpdate")) {
                        parsePatchInfo(json.getJSONObject("patch"))
                    } else null
                } else null
            }
        }
    }
    
    // 下载补丁
    suspend fun downloadPatch(downloadUrl: String): File? {
        val file = File(context.cacheDir, "patch_\${System.currentTimeMillis()}.zip")
        
        val request = Request.Builder()
            .url(downloadUrl)
            .get()
            .build()
        
        return withContext(Dispatchers.IO) {
            try {
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        response.body?.byteStream()?.use { input ->
                            file.outputStream().use { output ->
                                input.copyTo(output)
                            }
                        }
                        file
                    } else null
                }
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }
    
    // 上报结果
    suspend fun reportResult(patchId: String, success: Boolean, errorMsg: String? = null) {
        val json = JSONObject().apply {
            put("app_id", appId)
            put("patch_id", patchId)
            put("device_id", getDeviceId())
            put("success", success)
            errorMsg?.let { put("error_message", it) }
        }
        
        val body = json.toString()
            .toRequestBody("application/json".toMediaType())
        
        val request = Request.Builder()
            .url("$baseUrl/api/client/report")
            .post(body)
            .build()
        
        withContext(Dispatchers.IO) {
            try {
                client.newCall(request).execute()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    
    private fun getDeviceId(): String {
        return Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ANDROID_ID
        )
    }
    
    private fun parsePatchInfo(json: JSONObject): PatchInfo {
        return PatchInfo(
            id = json.getInt("id"),
            version = json.getString("version"),
            patchId = json.getString("patch_id"),
            baseVersion = json.getString("base_version"),
            fileSize = json.getLong("file_size"),
            md5 = json.getString("md5"),
            downloadUrl = json.getString("download_url"),
            forceUpdate = json.getBoolean("force_update"),
            description = json.optString("description")
        )
    }
}

data class PatchInfo(
    val id: Int,
    val version: String,
    val patchId: String,
    val baseVersion: String,
    val fileSize: Long,
    val md5: String,
    val downloadUrl: String,
    val forceUpdate: Boolean,
    val description: String
)`}),Se=H(()=>{var o;return`public class PatchManager {
    private final Context context;
    private final OkHttpClient client;
    private final String appId = "${((o=s.value)==null?void 0:o.app_id)||"YOUR_APP_ID"}";
    private final String baseUrl = "${j.value}";
    
    public PatchManager(Context context) {
        this.context = context;
        this.client = new OkHttpClient();
    }
    
    // 检查更新
    public PatchInfo checkUpdate(String currentVersion) throws IOException {
        String deviceId = getDeviceId();
        String url = baseUrl + "/api/client/check-update" +
            "?app_id=" + appId +
            "&version=" + currentVersion +
            "&device_id=" + deviceId;
        
        Request request = new Request.Builder()
            .url(url)
            .get()
            .build();
        
        try (Response response = client.newCall(request).execute()) {
            if (response.isSuccessful() && response.body() != null) {
                JSONObject json = new JSONObject(response.body().string());
                if (json.getBoolean("hasUpdate")) {
                    return parsePatchInfo(json.getJSONObject("patch"));
                }
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return null;
    }
    
    // 下载补丁
    public File downloadPatch(String downloadUrl) throws IOException {
        File file = new File(context.getCacheDir(), 
            "patch_" + System.currentTimeMillis() + ".zip");
        
        Request request = new Request.Builder()
            .url(downloadUrl)
            .get()
            .build();
        
        try (Response response = client.newCall(request).execute()) {
            if (response.isSuccessful() && response.body() != null) {
                try (InputStream input = response.body().byteStream();
                     FileOutputStream output = new FileOutputStream(file)) {
                    byte[] buffer = new byte[4096];
                    int bytesRead;
                    while ((bytesRead = input.read(buffer)) != -1) {
                        output.write(buffer, 0, bytesRead);
                    }
                }
                return file;
            }
        }
        return null;
    }
    
    // 上报结果
    public void reportResult(String patchId, boolean success, String errorMsg) 
            throws IOException, JSONException {
        JSONObject json = new JSONObject();
        json.put("app_id", appId);
        json.put("patch_id", patchId);
        json.put("device_id", getDeviceId());
        json.put("success", success);
        if (errorMsg != null) {
            json.put("error_message", errorMsg);
        }
        
        RequestBody body = RequestBody.create(
            json.toString(),
            MediaType.parse("application/json")
        );
        
        Request request = new Request.Builder()
            .url(baseUrl + "/api/client/report")
            .post(body)
            .build();
        
        client.newCall(request).execute();
    }
    
    private String getDeviceId() {
        return Settings.Secure.getString(
            context.getContentResolver(),
            Settings.Secure.ANDROID_ID
        );
    }
    
    private PatchInfo parsePatchInfo(JSONObject json) throws JSONException {
        return new PatchInfo(
            json.getInt("id"),
            json.getString("version"),
            json.getString("patch_id"),
            json.getString("base_version"),
            json.getLong("file_size"),
            json.getString("md5"),
            json.getString("download_url"),
            json.getBoolean("force_update"),
            json.optString("description")
        );
    }
}`}),R=async()=>{try{const{data:o}=await V.get(`/apps/${A.params.id}`);s.value=o,h.value==="versions"&&Z()}catch{u.error("加载应用详情失败"),we.back()}},Z=async()=>{ue.value=!0;try{const{data:o}=await V.get(`/versions/${s.value.app_id}`);te.value=o.versions}catch{u.error("加载版本列表失败")}finally{ue.value=!1}},Be=o=>{v.file=o.raw},Ne=async()=>{var o,e;if(!v.versionName||!v.versionCode||!v.file){u.warning("请填写完整信息并选择 APK 文件");return}ae.value=!0;try{const i=new FormData;i.append("file",v.file),i.append("versionName",v.versionName),i.append("versionCode",v.versionCode),i.append("description",v.description),i.append("changelog",v.changelog),i.append("downloadUrl",v.downloadUrl),i.append("isForceUpdate",v.isForceUpdate),i.append("minSupportedVersion",v.minSupportedVersion),await V.post(`/versions/${s.value.app_id}/upload`,i,{headers:{"Content-Type":"multipart/form-data"}}),u.success("版本上传成功"),Y.value=!1,Object.assign(v,{versionName:"",versionCode:null,description:"",changelog:"",downloadUrl:"",isForceUpdate:!1,minSupportedVersion:"",file:null}),pe.value&&pe.value.clearFiles(),Z(),R()}catch(i){u.error(((e=(o=i.response)==null?void 0:o.data)==null?void 0:e.error)||"上传版本失败")}finally{ae.value=!1}},Je=o=>{Object.assign(b,{id:o.id,version_name:o.version_name,version_code:o.version_code,description:o.description||"",changelog:o.changelog||"",download_url:o.download_url||"",is_force_update:o.is_force_update,min_supported_version:o.min_supported_version||"",status:o.status}),W.value=!0},Me=async()=>{var o,e;try{await V.put(`/versions/${b.id}`,{description:b.description,changelog:b.changelog,downloadUrl:b.download_url,isForceUpdate:b.is_force_update===1,minSupportedVersion:b.min_supported_version,status:b.status}),u.success("版本更新成功"),W.value=!1,Z(),R()}catch(i){u.error(((e=(o=i.response)==null?void 0:o.data)==null?void 0:e.error)||"更新版本失败")}},Le=o=>{const e=o.download_url||`${window.location.origin}/api/versions/download/${o.id}`;window.open(e,"_blank")},He=o=>{const e=o.download_url||`${window.location.origin}/api/versions/download/${o.id}`;navigator.clipboard.writeText(e).then(()=>{u.success("下载链接已复制")})},he=async o=>{var e,i;try{await ie.confirm("确定要删除此版本吗？删除后无法恢复。","确认删除",{type:"warning"}),await V.delete(`/versions/${o}`),u.success("版本删除成功"),Z()}catch(p){p!=="cancel"&&u.error(((i=(e=p.response)==null?void 0:e.data)==null?void 0:i.error)||"删除版本失败")}};Vl(h,o=>{o==="versions"&&Z()});const Ge=async()=>{try{const{data:o}=await V.get("/generate/check");B.value=o.available,ve.value=!0,o.available||console.warn("patch-cli 不可用:",o.error)}catch(o){console.error("检查 patch-cli 失败:",o),B.value=!1,ve.value=!0}},Ye=o=>{U.file=o.raw},We=async()=>{var o,e;if(!U.version){u.warning("请输入版本号");return}if(!U.base_version){u.warning("请输入基础版本号");return}if(!U.file){u.warning("请选择补丁文件");return}try{de.value=!0;const i=new FormData;i.append("file",U.file),i.append("app_id",A.params.id),i.append("version",U.version),i.append("base_version",U.base_version),i.append("description",U.description),i.append("force_update",U.force_update),i.append("package_name",s.value.package_name),i.append("app_id_string",s.value.app_id),console.log("📤 上传补丁，验证信息:"),console.log("  - 应用名称:",s.value.app_name),console.log("  - 包名:",s.value.package_name),console.log("  - app_id:",s.value.app_id),await V.post("/patches/upload",i,{headers:{"Content-Type":"multipart/form-data"}}),u.success("补丁上传成功"),G.value=!1,Object.assign(U,{version:"",base_version:"",description:"",force_update:!1,file:null}),R()}catch(i){u.error(((e=(o=i.response)==null?void 0:o.data)==null?void 0:e.error)||"上传失败")}finally{de.value=!1}},Qe=async()=>{try{await V.put(`/apps/${A.params.id}`,{app_name:s.value.app_name,package_name:s.value.package_name,description:s.value.description,icon:s.value.icon,status:s.value.status}),u.success("更新成功")}catch{u.error("更新失败")}},Xe=async()=>{try{if(s.value.require_signature&&(!s.value.keystore_password||!s.value.key_alias||!s.value.key_password)){u.warning("请完整配置 JKS 签名信息");return}if(s.value.require_encryption&&z.value)try{const{data:o}=await V.validateEncryptionKey(z.value);if(!o.valid){u.error("加密密钥格式无效");return}}catch{u.error("验证加密密钥失败");return}if(oe.value){const o=new FormData;o.append("keystore",oe.value),o.append("app_id",A.params.id);try{const{data:e}=await V.post("/apps/upload-keystore",o,{headers:{"Content-Type":"multipart/form-data"}});s.value.keystore_path=e.keystore_path,u.success("Keystore 文件上传成功")}catch{u.error("Keystore 文件上传失败");return}}await V.put(`/apps/${A.params.id}`,{app_name:s.value.app_name,package_name:s.value.package_name,description:s.value.description,icon:s.value.icon,status:s.value.status,require_signature:s.value.require_signature,require_encryption:s.value.require_encryption,keystore_path:s.value.keystore_path,keystore_password:s.value.keystore_password,key_alias:s.value.key_alias,key_password:s.value.key_password});try{s.value.require_encryption&&z.value?await V.updateEncryptionConfig(A.params.id,{enabled:!0,key:z.value}):s.value.require_encryption||await V.updateEncryptionConfig(A.params.id,{enabled:!1,key:null})}catch(o){console.error("更新加密配置失败:",o),u.warning("签名配置已保存，但加密配置更新失败")}u.success("安全配置已保存"),oe.value=null,ke.value=[],R()}catch{u.error("保存失败")}},Ze=async()=>{try{_e.value=!0;const{data:o}=await V.generateEncryptionKey();z.value=o.key,$.value={valid:!0,message:"✓ 密钥已生成"},u.success("密钥生成成功")}catch{u.error("生成密钥失败")}finally{_e.value=!1}},el=async()=>{var o,e;if(!z.value){u.warning("请先输入或生成密钥");return}try{const i="Hello, Patch Server!",{data:p}=await V.testEncryption(z.value,i);p.success&&p.match?(u.success("✓ 加密测试成功！密钥可以正常使用"),$.value={valid:!0,message:"✓ 密钥验证通过"}):(u.error("加密测试失败"),$.value={valid:!1,message:"✗ 密钥验证失败"})}catch(i){u.error("测试失败: "+(((e=(o=i.response)==null?void 0:o.data)==null?void 0:e.error)||i.message)),$.value={valid:!1,message:"✗ 密钥验证失败"}}},ll=async()=>{try{const{data:o}=await V.getEncryptionConfig(A.params.id);o.hasKey&&($.value={valid:!0,message:"✓ 已配置加密密钥"})}catch(o){console.error("加载加密配置失败:",o)}},tl=o=>{oe.value=o.raw},al=o=>{k.patchId=o.id,k.version=o.version,k.percentage=o.rollout_percentage||100,k.status=o.status,k.forceUpdate=o.force_update===1,X.value=!0},ol=async()=>{try{me.value=!0,await V.put(`/patches/${k.patchId}`,{rolloutPercentage:k.percentage,status:k.status,forceUpdate:k.forceUpdate}),u.success("灰度配置已更新"),X.value=!1,R()}catch{u.error("更新失败")}finally{me.value=!1}},nl=()=>{const o=k.percentage;return o===0?"补丁未发布":o<10?"小范围灰度测试":o<50?"中等规模灰度":o<100?"大规模灰度":"全量发布"},sl=()=>{const o=k.percentage;return o===0?"补丁不会推送给任何用户":o===100?"补丁会推送给所有符合条件的用户":`补丁会推送给约 ${o}% 的用户（基于设备 ID 哈希）`},rl=o=>{const e=(o.success_count||0)+(o.fail_count||0);return e===0?"N/A":`${((o.success_count||0)/e*100).toFixed(1)}%`},il=async()=>{try{await ie.confirm("确定要删除此应用吗？此操作不可恢复","警告",{type:"warning"}),await V.delete(`/apps/${A.params.id}`),u.success("删除成功"),we.push("/apps")}catch(o){o!=="cancel"&&u.error("删除失败")}},dl=async o=>{try{await ie.confirm("确定要删除此补丁吗？","警告",{type:"warning"}),await V.delete(`/patches/${o}`),u.success("删除成功"),R()}catch(e){e!=="cancel"&&u.error("删除失败")}},ul=o=>{window.open(`${V.defaults.baseURL.replace("/api","")}/downloads/${o.file_name}`,"_blank")},Ce=o=>o<1024?o+" B":o<1024*1024?(o/1024).toFixed(2)+" KB":(o/(1024*1024)).toFixed(2)+" MB",qe=o=>new Date(o).toLocaleString("zh-CN"),pl=()=>{N(s.value.app_id)},N=o=>{navigator.clipboard&&navigator.clipboard.writeText?navigator.clipboard.writeText(o).then(()=>{u.success("已复制到剪贴板")}).catch(()=>{Ie(o)}):Ie(o)},Ie=o=>{try{const e=document.createElement("textarea");e.value=o,e.style.position="fixed",e.style.opacity="0",document.body.appendChild(e),e.select();const i=document.execCommand("copy");document.body.removeChild(e),i?u.success("已复制到剪贴板"):u.error("复制失败，请手动复制")}catch{u.error("复制失败，请手动复制")}},Ae=o=>{let e="";o==="checkUpdate"?e=Ve.value:o==="report"&&(e=xe.value),N(e)},fl=o=>{w.baseApk=o.raw},cl=o=>{w.newApk=o.raw},vl=async()=>{var o,e,i,p;if(!w.version){u.warning("请输入版本号");return}if(!w.base_version){u.warning("请输入基础版本号");return}if(!w.baseApk){u.warning("请选择基准 APK");return}if(!w.newApk){u.warning("请选择新版本 APK");return}try{Q.value=!0,P.value=0;const g=new FormData;g.append("baseApk",w.baseApk),g.append("newApk",w.newApk),g.append("app_id",A.params.id),g.append("version",w.version),g.append("base_version",w.base_version),g.append("description",w.description),g.append("force_update",w.force_update),g.append("package_name",s.value.package_name),g.append("app_id_string",s.value.app_id),console.log("🔨 生成补丁，验证信息:"),console.log("  - 应用名称:",s.value.app_name),console.log("  - 包名:",s.value.package_name),console.log("  - app_id:",s.value.app_id);const F=setInterval(()=>{P.value<90&&(P.value+=10)},500),{data:I}=await V.post("/generate/patch",g,{headers:{"Content-Type":"multipart/form-data"}});clearInterval(F),P.value=100,u.success("补丁生成成功！"),Object.assign(w,{version:"",base_version:"",description:"",force_update:!1,baseApk:null,newApk:null}),fe.value&&fe.value.clearFiles(),ce.value&&ce.value.clearFiles(),setTimeout(()=>{P.value=0},2e3),h.value="patches",R()}catch(g){const F=((e=(o=g.response)==null?void 0:o.data)==null?void 0:e.error)||"生成补丁失败",I=(p=(i=g.response)==null?void 0:i.data)==null?void 0:p.details;I?ie.alert(I,F,{confirmButtonText:"确定",type:"warning"}):u.error(F),P.value=0}finally{Q.value=!1}};return xl(()=>{R(),Ge(),ll()}),(o,e)=>{const i=_("el-icon"),p=_("el-button"),g=_("el-tag"),F=_("el-empty"),I=_("el-tab-pane"),ne=_("el-divider"),m=_("el-input"),d=_("el-form-item"),J=_("el-radio"),ge=_("el-radio-group"),D=_("el-alert"),O=_("el-switch"),T=_("el-form"),ze=_("el-descriptions-item"),ml=_("el-descriptions"),K=_("el-table-column"),Pe=_("el-table"),M=_("el-collapse-item"),ye=_("el-collapse"),je=_("el-tabs"),_l=_("el-link"),ee=_("el-upload"),gl=_("el-progress"),se=_("el-dialog"),yl=_("el-input-number"),bl=_("el-slider"),wl=Cl("loading");return s.value?(c(),S("div",Rl,[n("div",Dl,[l(p,{onClick:e[0]||(e[0]=a=>o.$router.back()),text:""},{default:t(()=>[l(i,null,{default:t(()=>[l(q(ql))]),_:1}),e[59]||(e[59]=r(" 返回 ",-1))]),_:1}),n("div",Ol,[n("div",Kl,[s.value.icon?(c(),S("img",{key:0,src:s.value.icon,alt:""},null,8,Fl)):(c(),C(i,{key:1,size:32},{default:t(()=>[l(q(Il))]),_:1}))]),n("div",null,[n("h2",null,f(s.value.app_name),1),n("p",null,f(s.value.package_name),1)])]),l(p,{type:"primary",onClick:e[1]||(e[1]=a=>G.value=!0)},{default:t(()=>[l(i,null,{default:t(()=>[l(q(be))]),_:1}),e[60]||(e[60]=r(" 上传补丁 ",-1))]),_:1})]),l(je,{modelValue:h.value,"onUpdate:modelValue":e[29]||(e[29]=a=>h.value=a),class:"app-tabs"},{default:t(()=>[l(I,{label:"补丁列表",name:"patches"},{default:t(()=>[s.value.patches&&s.value.patches.length>0?(c(),S("div",Tl,[(c(!0),S(re,null,$e(s.value.patches,a=>(c(),S("div",{class:"patch-item",key:a.id},[n("div",El,[n("div",Bl,[l(g,{type:"primary",size:"large"},{default:t(()=>[r("v"+f(a.version),1)]),_:2},1024),a.force_update?(c(),C(i,{key:0,color:"#f56c6c"},{default:t(()=>[l(q(Re))]),_:1})):x("",!0),a.rollout_percentage<100?(c(),C(g,{key:1,type:"warning",size:"small",style:{"margin-left":"8px"}},{default:t(()=>[r(" 灰度 "+f(a.rollout_percentage)+"% ",1)]),_:2},1024)):x("",!0)]),n("div",Nl,[n("p",Jl,f(a.description||"无描述"),1),n("div",Ml,[n("span",null,"基础版本: "+f(a.base_version),1),n("span",null,"大小: "+f(Ce(a.file_size)),1),n("span",null,"下载: "+f(a.download_count),1),n("span",null,"成功率: "+f(rl(a)),1),n("span",null,f(qe(a.created_at)),1)])])]),n("div",Ll,[l(g,{type:a.status==="active"?"success":"info"},{default:t(()=>[r(f(a.status==="active"?"活跃":"停用"),1)]),_:2},1032,["type"]),l(p,{size:"small",onClick:L=>al(a)},{default:t(()=>[...e[61]||(e[61]=[r("灰度配置",-1)])]),_:1},8,["onClick"]),l(p,{size:"small",onClick:L=>ul(a)},{default:t(()=>[...e[62]||(e[62]=[r("下载",-1)])]),_:1},8,["onClick"]),l(p,{size:"small",type:"danger",onClick:L=>dl(a.id)},{default:t(()=>[...e[63]||(e[63]=[r("删除",-1)])]),_:1},8,["onClick"])])]))),128))])):(c(),C(F,{key:1,description:"还没有补丁"}))]),_:1}),l(I,{label:"应用设置",name:"settings"},{default:t(()=>[l(T,{model:s.value,"label-width":"140px",class:"settings-form"},{default:t(()=>[l(ne,{"content-position":"left"},{default:t(()=>[...e[64]||(e[64]=[r("基本信息",-1)])]),_:1}),l(d,{label:"App ID"},{default:t(()=>[l(m,{modelValue:s.value.app_id,"onUpdate:modelValue":e[2]||(e[2]=a=>s.value.app_id=a),disabled:""},{append:t(()=>[l(p,{onClick:pl,icon:q(E)},{default:t(()=>[...e[65]||(e[65]=[r("复制",-1)])]),_:1},8,["icon"])]),_:1},8,["modelValue"])]),_:1}),l(d,{label:"应用名称"},{default:t(()=>[l(m,{modelValue:s.value.app_name,"onUpdate:modelValue":e[3]||(e[3]=a=>s.value.app_name=a)},null,8,["modelValue"])]),_:1}),l(d,{label:"包名"},{default:t(()=>[l(m,{modelValue:s.value.package_name,"onUpdate:modelValue":e[4]||(e[4]=a=>s.value.package_name=a)},null,8,["modelValue"])]),_:1}),l(d,{label:"描述"},{default:t(()=>[l(m,{modelValue:s.value.description,"onUpdate:modelValue":e[5]||(e[5]=a=>s.value.description=a),type:"textarea",rows:3},null,8,["modelValue"])]),_:1}),l(d,{label:"图标 URL"},{default:t(()=>[l(m,{modelValue:s.value.icon,"onUpdate:modelValue":e[6]||(e[6]=a=>s.value.icon=a)},null,8,["modelValue"])]),_:1}),l(d,{label:"状态"},{default:t(()=>[l(ge,{modelValue:s.value.status,"onUpdate:modelValue":e[7]||(e[7]=a=>s.value.status=a)},{default:t(()=>[l(J,{label:"active"},{default:t(()=>[...e[66]||(e[66]=[r("活跃",-1)])]),_:1}),l(J,{label:"inactive"},{default:t(()=>[...e[67]||(e[67]=[r("停用",-1)])]),_:1})]),_:1},8,["modelValue"])]),_:1}),l(ne,{"content-position":"left"},{default:t(()=>[...e[68]||(e[68]=[r("强制更新配置",-1)])]),_:1}),l(D,{title:"强制大版本更新",type:"info",closable:!1,style:{"margin-bottom":"20px"}},{default:t(()=>[...e[69]||(e[69]=[n("p",{style:{margin:"0"}},"当用户版本低于设定的最新版本时，将强制用户更新到最新版本，无法使用热更新补丁。",-1)])]),_:1}),l(d,{label:"启用强制更新"},{default:t(()=>[l(O,{modelValue:s.value.force_update_enabled,"onUpdate:modelValue":e[8]||(e[8]=a=>s.value.force_update_enabled=a),"active-value":1,"inactive-value":0},null,8,["modelValue"]),e[70]||(e[70]=n("span",{style:{"margin-left":"12px",color:"#909399","font-size":"13px"}}," 开启后，低于最新版本的用户将被强制更新 ",-1))]),_:1}),s.value.force_update_enabled?(c(),C(d,{key:0,label:"最新版本号"},{default:t(()=>[l(m,{modelValue:s.value.latest_version,"onUpdate:modelValue":e[9]||(e[9]=a=>s.value.latest_version=a),placeholder:"如: 1.5.0"},{prepend:t(()=>[...e[71]||(e[71]=[r("v",-1)])]),_:1},8,["modelValue"]),e[72]||(e[72]=n("div",{style:{color:"#909399","font-size":"12px","margin-top":"4px"}}," 低于此版本的用户将被强制更新 ",-1))]),_:1})):x("",!0),s.value.force_update_enabled?(c(),C(d,{key:1,label:"下载地址"},{default:t(()=>[l(m,{modelValue:s.value.force_update_url,"onUpdate:modelValue":e[10]||(e[10]=a=>s.value.force_update_url=a),placeholder:"APK 下载地址"},null,8,["modelValue"]),e[73]||(e[73]=n("div",{style:{color:"#909399","font-size":"12px","margin-top":"4px"}}," 可以使用版本管理中上传的 APK，或填写外部下载链接 ",-1))]),_:1})):x("",!0),s.value.force_update_enabled?(c(),C(d,{key:2,label:"更新提示"},{default:t(()=>[l(m,{modelValue:s.value.force_update_message,"onUpdate:modelValue":e[11]||(e[11]=a=>s.value.force_update_message=a),type:"textarea",rows:3,placeholder:"发现新版本，请更新到最新版本"},null,8,["modelValue"])]),_:1})):x("",!0),l(d,null,{default:t(()=>[l(p,{type:"primary",onClick:Qe},{default:t(()=>[...e[74]||(e[74]=[r("保存设置",-1)])]),_:1}),l(p,{type:"danger",onClick:il},{default:t(()=>[...e[75]||(e[75]=[r("删除应用",-1)])]),_:1})]),_:1})]),_:1},8,["model"])]),_:1}),l(I,{label:"版本管理",name:"versions"},{default:t(()=>[n("div",Hl,[n("div",hl,[e[77]||(e[77]=n("div",null,[n("h3",null,"大版本管理"),n("p",{style:{color:"#909399",margin:"4px 0 0 0"}},"管理应用的完整 APK 版本，用于强制更新")],-1)),l(p,{type:"primary",onClick:e[12]||(e[12]=a=>Y.value=!0)},{default:t(()=>[l(i,null,{default:t(()=>[l(q(be))]),_:1}),e[76]||(e[76]=r(" 上传新版本 ",-1))]),_:1})]),te.value&&te.value.length>0?Al((c(),S("div",Gl,[(c(!0),S(re,null,$e(te.value,a=>(c(),S("div",{class:"version-item",key:a.id},[n("div",Yl,[n("div",Wl,[l(g,{type:"primary",size:"large"},{default:t(()=>[r("v"+f(a.version_name),1)]),_:2},1024),l(g,{size:"small",style:{"margin-left":"8px"}},{default:t(()=>[r("Code: "+f(a.version_code),1)]),_:2},1024),a.is_force_update?(c(),C(i,{key:0,color:"#f56c6c",style:{"margin-left":"8px"}},{default:t(()=>[l(q(Re))]),_:1})):x("",!0),a.is_force_update?(c(),S("span",Ql," 强制更新 ")):x("",!0)]),n("div",Xl,[n("p",Zl,f(a.description||"无描述"),1),a.changelog?(c(),S("div",et,[e[78]||(e[78]=n("strong",null,"更新说明：",-1)),n("pre",null,f(a.changelog),1)])):x("",!0),n("div",lt,[n("span",null,"大小: "+f(Ce(a.file_size)),1),n("span",null,"下载: "+f(a.download_count),1),n("span",null,"MD5: "+f(a.md5.substring(0,8))+"...",1),n("span",null,f(qe(a.created_at)),1),a.creator_name?(c(),S("span",tt,"上传者: "+f(a.creator_name),1)):x("",!0)])])]),n("div",at,[l(g,{type:a.status==="active"?"success":"info"},{default:t(()=>[r(f(a.status==="active"?"活跃":"停用"),1)]),_:2},1032,["type"]),l(p,{size:"small",onClick:L=>Je(a)},{default:t(()=>[...e[79]||(e[79]=[r("编辑",-1)])]),_:1},8,["onClick"]),l(p,{size:"small",onClick:L=>Le(a)},{default:t(()=>[...e[80]||(e[80]=[r("下载",-1)])]),_:1},8,["onClick"]),l(p,{size:"small",onClick:L=>He(a)},{default:t(()=>[...e[81]||(e[81]=[r("复制链接",-1)])]),_:1},8,["onClick"]),l(p,{size:"small",type:"danger",onClick:L=>he(a.id)},{default:t(()=>[...e[82]||(e[82]=[r("删除",-1)])]),_:1},8,["onClick"])])]))),128))])),[[wl,ue.value]]):(c(),C(F,{key:1,description:"还没有上传版本"}))])]),_:1}),l(I,{label:"API 对接",name:"api"},{default:t(()=>[n("div",ot,[l(D,{title:"客户端对接指南",type:"info",closable:!1,style:{"margin-bottom":"24px"}},{default:t(()=>[...e[83]||(e[83]=[n("p",{style:{margin:"0"}},"使用以下 API 接口在您的 Android 应用中集成热更新功能。",-1)])]),_:1}),n("div",nt,[e[84]||(e[84]=n("h3",null,"基本信息",-1)),l(ml,{column:1,border:""},{default:t(()=>[l(ze,{label:"App ID"},{default:t(()=>[n("div",st,[n("code",null,f(s.value.app_id),1),l(p,{size:"small",text:"",onClick:e[13]||(e[13]=a=>N(s.value.app_id))},{default:t(()=>[l(i,null,{default:t(()=>[l(q(E))]),_:1})]),_:1})])]),_:1}),l(ze,{label:"API 地址"},{default:t(()=>[n("div",rt,[n("code",null,f(j.value),1),l(p,{size:"small",text:"",onClick:e[14]||(e[14]=a=>N(j.value))},{default:t(()=>[l(i,null,{default:t(()=>[l(q(E))]),_:1})]),_:1})])]),_:1})]),_:1})]),n("div",it,[e[88]||(e[88]=n("h3",null,"1. 检查更新",-1)),e[89]||(e[89]=n("p",{class:"api-desc"},"客户端调用此接口检查是否有可用的补丁更新。",-1)),n("div",dt,[n("div",ut,[l(g,{type:"success"},{default:t(()=>[...e[85]||(e[85]=[r("GET",-1)])]),_:1}),e[87]||(e[87]=n("code",{class:"api-url"},"/api/client/check-update",-1)),l(p,{size:"small",onClick:e[15]||(e[15]=a=>Ae("checkUpdate"))},{default:t(()=>[l(i,null,{default:t(()=>[l(q(E))]),_:1}),e[86]||(e[86]=r(" 复制示例 ",-1))]),_:1})]),l(ye,null,{default:t(()=>[l(M,{title:"请求参数",name:"1"},{default:t(()=>[l(Pe,{data:Ke,size:"small",border:""},{default:t(()=>[l(K,{prop:"name",label:"参数名",width:"150"}),l(K,{prop:"type",label:"类型",width:"100"}),l(K,{prop:"required",label:"必填",width:"80"},{default:t(({row:a})=>[l(g,{type:a.required?"danger":"info",size:"small"},{default:t(()=>[r(f(a.required?"是":"否"),1)]),_:2},1032,["type"])]),_:1}),l(K,{prop:"desc",label:"说明"})]),_:1})]),_:1}),l(M,{title:"请求示例",name:"2"},{default:t(()=>[n("pre",pt,f(Ve.value),1)]),_:1}),l(M,{title:"响应示例",name:"3"},{default:t(()=>[n("pre",{class:"code-block"},f(Te))]),_:1})]),_:1})])]),n("div",ft,[e[92]||(e[92]=n("h3",null,"2. 下载补丁",-1)),e[93]||(e[93]=n("p",{class:"api-desc"},"获取到更新信息后，使用 download_url 下载补丁文件。",-1)),n("div",ct,[n("div",vt,[l(g,{type:"success"},{default:t(()=>[...e[90]||(e[90]=[r("GET",-1)])]),_:1}),e[91]||(e[91]=n("code",{class:"api-url"},"/downloads/{file_name}",-1))]),l(ye,null,{default:t(()=>[l(M,{title:"下载示例",name:"1"},{default:t(()=>[n("pre",mt,f(Ee.value),1)]),_:1})]),_:1})])]),n("div",_t,[e[97]||(e[97]=n("h3",null,"3. 上报应用结果",-1)),e[98]||(e[98]=n("p",{class:"api-desc"},"补丁应用完成后，上报应用结果（成功或失败）。",-1)),n("div",gt,[n("div",yt,[l(g,{type:"primary"},{default:t(()=>[...e[94]||(e[94]=[r("POST",-1)])]),_:1}),e[96]||(e[96]=n("code",{class:"api-url"},"/api/client/report",-1)),l(p,{size:"small",onClick:e[16]||(e[16]=a=>Ae("report"))},{default:t(()=>[l(i,null,{default:t(()=>[l(q(E))]),_:1}),e[95]||(e[95]=r(" 复制示例 ",-1))]),_:1})]),l(ye,null,{default:t(()=>[l(M,{title:"请求参数",name:"1"},{default:t(()=>[l(Pe,{data:Fe,size:"small",border:""},{default:t(()=>[l(K,{prop:"name",label:"参数名",width:"150"}),l(K,{prop:"type",label:"类型",width:"100"}),l(K,{prop:"required",label:"必填",width:"80"},{default:t(({row:a})=>[l(g,{type:a.required?"danger":"info",size:"small"},{default:t(()=>[r(f(a.required?"是":"否"),1)]),_:2},1032,["type"])]),_:1}),l(K,{prop:"desc",label:"说明"})]),_:1})]),_:1}),l(M,{title:"请求示例",name:"2"},{default:t(()=>[n("pre",bt,f(xe.value),1)]),_:1})]),_:1})])]),n("div",wt,[e[101]||(e[101]=n("h3",null,"Android 客户端集成示例",-1)),l(je,null,{default:t(()=>[l(I,{label:"Kotlin"},{default:t(()=>[n("div",kt,[l(p,{size:"small",onClick:e[17]||(e[17]=a=>N(Ue.value))},{default:t(()=>[l(i,null,{default:t(()=>[l(q(E))]),_:1}),e[99]||(e[99]=r(" 复制代码 ",-1))]),_:1})]),n("pre",Vt,f(Ue.value),1)]),_:1}),l(I,{label:"Java"},{default:t(()=>[n("div",xt,[l(p,{size:"small",onClick:e[18]||(e[18]=a=>N(Se.value))},{default:t(()=>[l(i,null,{default:t(()=>[l(q(E))]),_:1}),e[100]||(e[100]=r(" 复制代码 ",-1))]),_:1})]),n("pre",Ut,f(Se.value),1)]),_:1})]),_:1})])])]),_:1}),l(I,{label:"生成补丁",name:"generate"},{default:t(()=>[n("div",St,[!B.value&&ve.value?(c(),C(D,{key:0,title:"patch-cli 不可用",type:"error",closable:!1,style:{"margin-bottom":"20px"}},{default:t(()=>[n("div",Ct,[e[103]||(e[103]=n("p",{style:{margin:"0 0 12px 0","font-weight":"600"}},"自动生成补丁功能需要以下环境：",-1)),e[104]||(e[104]=n("ul",{style:{margin:"0","padding-left":"20px"}},[n("li",null,[n("strong",null,"Java 11+"),r("：运行 patch-cli 需要 Java 环境")]),n("li",null,[n("strong",null,"patch-cli JAR"),r("：补丁生成工具")])],-1)),e[105]||(e[105]=n("p",{style:{margin:"12px 0 0 0",color:"#666"}},[n("strong",null,"解决方案：")],-1)),e[106]||(e[106]=n("ol",{style:{margin:"4px 0 0 0","padding-left":"20px",color:"#666"}},[n("li",null,[r("检查服务器是否安装 Java："),n("code",null,"java -version")]),n("li",null,"确认 patch-cli JAR 文件路径配置正确"),n("li",null,'或者使用"上传补丁"功能手动上传本地生成的补丁')],-1)),n("p",qt,[l(_l,{type:"primary",href:"https://github.com/706412584/Android_hotupdate/blob/main/patch-server/docs/PATCH-CLI-INTEGRATION.md",target:"_blank"},{default:t(()=>[...e[102]||(e[102]=[r(" 查看完整配置指南 → ",-1)])]),_:1})])])]),_:1})):B.value?(c(),C(D,{key:1,title:"patch-cli 已就绪",type:"success",closable:!1,style:{"margin-bottom":"20px"}},{default:t(()=>[...e[107]||(e[107]=[n("p",{style:{margin:"0"}},"✅ Java 环境和 patch-cli 工具已配置，可以自动生成补丁。",-1)])]),_:1})):(c(),C(D,{key:2,title:"正在检查 patch-cli 环境...",type:"info",closable:!1,style:{"margin-bottom":"20px"}},{default:t(()=>[...e[108]||(e[108]=[n("p",{style:{margin:"0"}},"请稍候...",-1)])]),_:1})),B.value?(c(),C(D,{key:3,title:"使用 patch-cli 自动生成补丁",type:"info",closable:!1,style:{"margin-bottom":"20px"}},{default:t(()=>[e[109]||(e[109]=n("p",{style:{margin:"0"}},"上传基准 APK 和新版本 APK，服务端将自动生成补丁文件。",-1)),n("p",It,[s.value.require_signature&&s.value.keystore_path?(c(),S("span",At," ✅ 已配置签名，生成的补丁将自动签名 ")):s.value.require_signature&&!s.value.keystore_path?(c(),S("span",zt," ⚠️ 已开启签名验证，但未上传 Keystore 文件，补丁将不会签名 ")):(c(),S("span",Pt,' ℹ️ 未配置签名，生成的补丁不会签名（可在"安全配置"中配置） '))])]),_:1})):x("",!0),B.value?(c(),C(T,{key:4,model:w,"label-width":"120px",class:"settings-form"},{default:t(()=>[l(d,{label:"版本号",required:""},{default:t(()=>[l(m,{modelValue:w.version,"onUpdate:modelValue":e[19]||(e[19]=a=>w.version=a),placeholder:"如: 1.0.1"},null,8,["modelValue"])]),_:1}),l(d,{label:"基础版本",required:""},{default:t(()=>[l(m,{modelValue:w.base_version,"onUpdate:modelValue":e[20]||(e[20]=a=>w.base_version=a),placeholder:"如: 1.0.0"},null,8,["modelValue"])]),_:1}),l(d,{label:"基准 APK",required:""},{default:t(()=>[l(ee,{ref_key:"baseApkRef",ref:fe,"auto-upload":!1,limit:1,accept:".apk","on-change":fl},{tip:t(()=>[...e[111]||(e[111]=[n("div",{style:{"font-size":"12px",color:"#999","margin-top":"4px"}}," 当前应用版本的 APK 文件 ",-1)])]),default:t(()=>[l(p,null,{default:t(()=>[...e[110]||(e[110]=[r("选择基准 APK",-1)])]),_:1})]),_:1},512)]),_:1}),l(d,{label:"新版本 APK",required:""},{default:t(()=>[l(ee,{ref_key:"newApkRef",ref:ce,"auto-upload":!1,limit:1,accept:".apk","on-change":cl},{tip:t(()=>[...e[113]||(e[113]=[n("div",{style:{"font-size":"12px",color:"#999","margin-top":"4px"}}," 要更新到的新版本 APK 文件 ",-1)])]),default:t(()=>[l(p,null,{default:t(()=>[...e[112]||(e[112]=[r("选择新版本 APK",-1)])]),_:1})]),_:1},512)]),_:1}),l(d,{label:"描述"},{default:t(()=>[l(m,{modelValue:w.description,"onUpdate:modelValue":e[21]||(e[21]=a=>w.description=a),type:"textarea",rows:3,placeholder:"补丁更新内容说明"},null,8,["modelValue"])]),_:1}),l(d,{label:"强制更新"},{default:t(()=>[l(O,{modelValue:w.force_update,"onUpdate:modelValue":e[22]||(e[22]=a=>w.force_update=a)},null,8,["modelValue"])]),_:1}),l(d,null,{default:t(()=>[l(p,{type:"primary",onClick:vl,loading:Q.value},{default:t(()=>[Q.value?x("",!0):(c(),C(i,{key:0},{default:t(()=>[l(q(zl))]),_:1})),r(" "+f(Q.value?"生成中...":"生成补丁"),1)]),_:1},8,["loading"])]),_:1})]),_:1},8,["model"])):x("",!0),P.value>0?(c(),C(gl,{key:5,percentage:P.value,status:P.value===100?"success":void 0,style:{"margin-top":"20px"}},null,8,["percentage","status"])):x("",!0)])]),_:1}),l(I,{label:"安全配置",name:"security"},{default:t(()=>[l(T,{model:s.value,"label-width":"140px",class:"settings-form"},{default:t(()=>[l(D,{title:"安全建议",type:"info",closable:!1,style:{"margin-bottom":"20px"}},{default:t(()=>[...e[114]||(e[114]=[n("ul",{style:{margin:"0","padding-left":"20px"}},[n("li",null,"APK 签名验证：防止补丁被篡改，推荐开启"),n("li",null,"补丁加密：保护补丁内容，敏感应用建议开启"),n("li",null,"开发测试时可以关闭验证")],-1)])]),_:1}),l(d,{label:"APK 签名验证"},{default:t(()=>[l(O,{modelValue:s.value.require_signature,"onUpdate:modelValue":e[23]||(e[23]=a=>s.value.require_signature=a),"active-value":1,"inactive-value":0},null,8,["modelValue"]),e[115]||(e[115]=n("div",{style:{"font-size":"12px",color:"#999","margin-top":"4px"}}," 开启后，只能应用已签名的补丁 ",-1))]),_:1}),s.value.require_signature?(c(),S(re,{key:0},[l(ne,{"content-position":"left"},{default:t(()=>[...e[116]||(e[116]=[r("JKS 签名配置",-1)])]),_:1}),l(d,{label:"Keystore 文件"},{default:t(()=>[l(ee,{ref_key:"keystoreUploadRef",ref:Oe,"auto-upload":!1,limit:1,accept:".jks,.keystore,.bks","on-change":tl,"file-list":ke.value},{tip:t(()=>[...e[118]||(e[118]=[n("div",{style:{"font-size":"12px",color:"#999","margin-top":"4px"}}," 支持 .jks、.keystore 或 .bks 文件 ",-1)])]),default:t(()=>[l(p,{size:"small"},{default:t(()=>[l(i,null,{default:t(()=>[l(q(be))]),_:1}),e[117]||(e[117]=r(" 选择 Keystore 文件 ",-1))]),_:1})]),_:1},8,["file-list"]),s.value.keystore_path?(c(),S("div",jt," ✓ 已上传："+f(s.value.keystore_path.split("/").pop()||s.value.keystore_path.split("\\").pop()),1)):x("",!0)]),_:1}),l(d,{label:"密钥库密码"},{default:t(()=>[l(m,{modelValue:s.value.keystore_password,"onUpdate:modelValue":e[24]||(e[24]=a=>s.value.keystore_password=a),type:"password","show-password":"",placeholder:"Keystore Password"},null,8,["modelValue"])]),_:1}),l(d,{label:"密钥别名"},{default:t(()=>[l(m,{modelValue:s.value.key_alias,"onUpdate:modelValue":e[25]||(e[25]=a=>s.value.key_alias=a),placeholder:"Key Alias"},null,8,["modelValue"])]),_:1}),l(d,{label:"密钥密码"},{default:t(()=>[l(m,{modelValue:s.value.key_password,"onUpdate:modelValue":e[26]||(e[26]=a=>s.value.key_password=a),type:"password","show-password":"",placeholder:"Key Password"},null,8,["modelValue"])]),_:1})],64)):x("",!0),l(d,{label:"强制补丁加密"},{default:t(()=>[l(O,{modelValue:s.value.require_encryption,"onUpdate:modelValue":e[27]||(e[27]=a=>s.value.require_encryption=a),"active-value":1,"inactive-value":0},null,8,["modelValue"]),e[119]||(e[119]=n("div",{style:{"font-size":"12px",color:"#999","margin-top":"4px"}}," 开启后，只能应用已加密的补丁 ",-1))]),_:1}),s.value.require_encryption?(c(),S(re,{key:1},[l(ne,{"content-position":"left"},{default:t(()=>[...e[120]||(e[120]=[r("加密配置",-1)])]),_:1}),l(d,{label:"加密密钥"},{default:t(()=>[l(m,{modelValue:z.value,"onUpdate:modelValue":e[28]||(e[28]=a=>z.value=a),type:"password","show-password":"",placeholder:"64 位十六进制密钥",style:{width:"400px"}},{append:t(()=>[l(p,{onClick:Ze,loading:_e.value},{default:t(()=>[l(i,null,{default:t(()=>[l(q(Pl))]),_:1}),e[121]||(e[121]=r(" 生成 ",-1))]),_:1},8,["loading"])]),_:1},8,["modelValue"]),e[122]||(e[122]=n("div",{style:{"font-size":"12px",color:"#999","margin-top":"4px"}}," AES-256 加密密钥（64 位十六进制字符） ",-1)),$.value?(c(),S("div",{key:0,style:jl({fontSize:"12px",marginTop:"4px",color:$.value.valid?"#67c23a":"#f56c6c"})},f($.value.message),5)):x("",!0)]),_:1}),l(d,{label:"测试加密"},{default:t(()=>[l(p,{size:"small",onClick:el,disabled:!z.value},{default:t(()=>[l(i,null,{default:t(()=>[l(q($l))]),_:1}),e[123]||(e[123]=r(" 测试加密/解密 ",-1))]),_:1},8,["disabled"]),e[124]||(e[124]=n("div",{style:{"font-size":"12px",color:"#999","margin-top":"4px"}}," 验证密钥是否可以正常加密和解密 ",-1))]),_:1})],64)):x("",!0),l(d,null,{default:t(()=>[l(p,{type:"primary",onClick:Xe},{default:t(()=>[...e[125]||(e[125]=[r("保存安全配置",-1)])]),_:1})]),_:1})]),_:1},8,["model"])]),_:1})]),_:1},8,["modelValue"]),l(se,{modelValue:G.value,"onUpdate:modelValue":e[35]||(e[35]=a=>G.value=a),title:"上传补丁",width:"600px"},{footer:t(()=>[l(p,{onClick:e[34]||(e[34]=a=>G.value=!1)},{default:t(()=>[...e[131]||(e[131]=[r("取消",-1)])]),_:1}),l(p,{type:"primary",onClick:We,loading:de.value},{default:t(()=>[...e[132]||(e[132]=[r("上传",-1)])]),_:1},8,["loading"])]),default:t(()=>[l(T,{model:U,"label-width":"100px"},{default:t(()=>[l(d,{label:"版本号",required:""},{extra:t(()=>[...e[126]||(e[126]=[n("span",{style:{"font-size":"12px",color:"#999"}},"补丁的目标版本号",-1)])]),default:t(()=>[l(m,{modelValue:U.version,"onUpdate:modelValue":e[30]||(e[30]=a=>U.version=a),placeholder:"如: 1.0.1"},null,8,["modelValue"])]),_:1}),l(d,{label:"基础版本",required:""},{extra:t(()=>[...e[127]||(e[127]=[n("span",{style:{"font-size":"12px",color:"#999"}},"当前应用的版本号",-1)])]),default:t(()=>[l(m,{modelValue:U.base_version,"onUpdate:modelValue":e[31]||(e[31]=a=>U.base_version=a),placeholder:"如: 1.0.0"},null,8,["modelValue"])]),_:1}),l(d,{label:"描述"},{default:t(()=>[l(m,{modelValue:U.description,"onUpdate:modelValue":e[32]||(e[32]=a=>U.description=a),type:"textarea",rows:3,placeholder:"补丁更新内容说明"},null,8,["modelValue"])]),_:1}),l(d,{label:"补丁文件",required:""},{default:t(()=>[l(ee,{ref_key:"uploadRef",ref:De,"auto-upload":!1,limit:1,accept:".patch,.zip","on-change":Ye},{tip:t(()=>[...e[129]||(e[129]=[n("div",{style:{"font-size":"12px",color:"#999","margin-top":"4px"}}," 支持 .patch 或 .zip 格式，最大 100MB ",-1)])]),default:t(()=>[l(p,null,{default:t(()=>[...e[128]||(e[128]=[r("选择文件",-1)])]),_:1})]),_:1},512)]),_:1}),l(d,{label:"强制更新"},{extra:t(()=>[...e[130]||(e[130]=[n("span",{style:{"font-size":"12px",color:"#999"}},"开启后用户必须更新才能使用",-1)])]),default:t(()=>[l(O,{modelValue:U.force_update,"onUpdate:modelValue":e[33]||(e[33]=a=>U.force_update=a)},null,8,["modelValue"])]),_:1})]),_:1},8,["model"])]),_:1},8,["modelValue"]),l(se,{modelValue:Y.value,"onUpdate:modelValue":e[44]||(e[44]=a=>Y.value=a),title:"上传新版本",width:"600px"},{footer:t(()=>[l(p,{onClick:e[43]||(e[43]=a=>Y.value=!1)},{default:t(()=>[...e[140]||(e[140]=[r("取消",-1)])]),_:1}),l(p,{type:"primary",onClick:Ne,loading:ae.value},{default:t(()=>[r(f(ae.value?"上传中...":"上传"),1)]),_:1},8,["loading"])]),default:t(()=>[l(T,{model:v,"label-width":"120px"},{default:t(()=>[l(d,{label:"版本名称",required:""},{default:t(()=>[l(m,{modelValue:v.versionName,"onUpdate:modelValue":e[36]||(e[36]=a=>v.versionName=a),placeholder:"如: 1.5.0"},{prepend:t(()=>[...e[133]||(e[133]=[r("v",-1)])]),_:1},8,["modelValue"])]),_:1}),l(d,{label:"版本号",required:""},{default:t(()=>[l(yl,{modelValue:v.versionCode,"onUpdate:modelValue":e[37]||(e[37]=a=>v.versionCode=a),min:1,placeholder:"如: 5",style:{width:"100%"}},null,8,["modelValue"]),e[134]||(e[134]=n("div",{style:{"font-size":"12px",color:"#999","margin-top":"4px"}}," 整数版本号，必须大于之前的版本 ",-1))]),_:1}),l(d,{label:"APK 文件",required:""},{default:t(()=>[l(ee,{ref_key:"versionUploadRef",ref:pe,"auto-upload":!1,limit:1,accept:".apk","on-change":Be},{tip:t(()=>[...e[136]||(e[136]=[n("div",{style:{"font-size":"12px",color:"#999","margin-top":"4px"}}," 支持 .apk 格式，最大 500MB ",-1)])]),default:t(()=>[l(p,null,{default:t(()=>[...e[135]||(e[135]=[r("选择 APK 文件",-1)])]),_:1})]),_:1},512)]),_:1}),l(d,{label:"版本描述"},{default:t(()=>[l(m,{modelValue:v.description,"onUpdate:modelValue":e[38]||(e[38]=a=>v.description=a),type:"textarea",rows:2,placeholder:"简短描述"},null,8,["modelValue"])]),_:1}),l(d,{label:"更新说明"},{default:t(()=>[l(m,{modelValue:v.changelog,"onUpdate:modelValue":e[39]||(e[39]=a=>v.changelog=a),type:"textarea",rows:4,placeholder:"详细的更新内容"},null,8,["modelValue"])]),_:1}),l(d,{label:"下载地址"},{default:t(()=>[l(m,{modelValue:v.downloadUrl,"onUpdate:modelValue":e[40]||(e[40]=a=>v.downloadUrl=a),placeholder:"留空则使用服务器地址"},null,8,["modelValue"]),e[137]||(e[137]=n("div",{style:{"font-size":"12px",color:"#999","margin-top":"4px"}}," 可选，填写外部下载链接（如应用商店） ",-1))]),_:1}),l(d,{label:"强制更新"},{default:t(()=>[l(O,{modelValue:v.isForceUpdate,"onUpdate:modelValue":e[41]||(e[41]=a=>v.isForceUpdate=a)},null,8,["modelValue"]),e[138]||(e[138]=n("div",{style:{"font-size":"12px",color:"#999","margin-top":"4px"}}," 开启后，低于此版本的用户将被强制更新 ",-1))]),_:1}),v.isForceUpdate?(c(),C(d,{key:0,label:"最低支持版本"},{default:t(()=>[l(m,{modelValue:v.minSupportedVersion,"onUpdate:modelValue":e[42]||(e[42]=a=>v.minSupportedVersion=a),placeholder:"如: 1.0.0"},null,8,["modelValue"]),e[139]||(e[139]=n("div",{style:{"font-size":"12px",color:"#999","margin-top":"4px"}}," 低于此版本的用户将被强制更新 ",-1))]),_:1})):x("",!0)]),_:1},8,["model"])]),_:1},8,["modelValue"]),l(se,{modelValue:W.value,"onUpdate:modelValue":e[52]||(e[52]=a=>W.value=a),title:"编辑版本",width:"600px"},{footer:t(()=>[l(p,{onClick:e[51]||(e[51]=a=>W.value=!1)},{default:t(()=>[...e[143]||(e[143]=[r("取消",-1)])]),_:1}),l(p,{type:"primary",onClick:Me},{default:t(()=>[...e[144]||(e[144]=[r("保存",-1)])]),_:1})]),default:t(()=>[l(T,{model:b,"label-width":"120px"},{default:t(()=>[l(d,{label:"版本"},{default:t(()=>[l(m,{value:`v${b.version_name} (${b.version_code})`,disabled:""},null,8,["value"])]),_:1}),l(d,{label:"版本描述"},{default:t(()=>[l(m,{modelValue:b.description,"onUpdate:modelValue":e[45]||(e[45]=a=>b.description=a),type:"textarea",rows:2},null,8,["modelValue"])]),_:1}),l(d,{label:"更新说明"},{default:t(()=>[l(m,{modelValue:b.changelog,"onUpdate:modelValue":e[46]||(e[46]=a=>b.changelog=a),type:"textarea",rows:4},null,8,["modelValue"])]),_:1}),l(d,{label:"下载地址"},{default:t(()=>[l(m,{modelValue:b.download_url,"onUpdate:modelValue":e[47]||(e[47]=a=>b.download_url=a)},null,8,["modelValue"])]),_:1}),l(d,{label:"强制更新"},{default:t(()=>[l(O,{modelValue:b.is_force_update,"onUpdate:modelValue":e[48]||(e[48]=a=>b.is_force_update=a),"active-value":1,"inactive-value":0},null,8,["modelValue"])]),_:1}),b.is_force_update?(c(),C(d,{key:0,label:"最低支持版本"},{default:t(()=>[l(m,{modelValue:b.min_supported_version,"onUpdate:modelValue":e[49]||(e[49]=a=>b.min_supported_version=a)},null,8,["modelValue"])]),_:1})):x("",!0),l(d,{label:"状态"},{default:t(()=>[l(ge,{modelValue:b.status,"onUpdate:modelValue":e[50]||(e[50]=a=>b.status=a)},{default:t(()=>[l(J,{label:"active"},{default:t(()=>[...e[141]||(e[141]=[r("活跃",-1)])]),_:1}),l(J,{label:"inactive"},{default:t(()=>[...e[142]||(e[142]=[r("停用",-1)])]),_:1})]),_:1},8,["modelValue"])]),_:1})]),_:1},8,["model"])]),_:1},8,["modelValue"]),l(se,{modelValue:X.value,"onUpdate:modelValue":e[58]||(e[58]=a=>X.value=a),title:"灰度发布配置",width:"600px"},{footer:t(()=>[l(p,{onClick:e[57]||(e[57]=a=>X.value=!1)},{default:t(()=>[...e[149]||(e[149]=[r("取消",-1)])]),_:1}),l(p,{type:"primary",onClick:ol,loading:me.value},{default:t(()=>[...e[150]||(e[150]=[r("保存配置",-1)])]),_:1},8,["loading"])]),default:t(()=>[l(T,{model:k,"label-width":"120px"},{default:t(()=>[l(d,{label:"补丁版本"},{default:t(()=>[l(m,{modelValue:k.version,"onUpdate:modelValue":e[53]||(e[53]=a=>k.version=a),disabled:""},null,8,["modelValue"])]),_:1}),l(d,{label:"灰度百分比"},{default:t(()=>[l(bl,{modelValue:k.percentage,"onUpdate:modelValue":e[54]||(e[54]=a=>k.percentage=a),marks:{0:"0%",25:"25%",50:"50%",75:"75%",100:"100%"},step:5},null,8,["modelValue"]),n("div",$t,[e[145]||(e[145]=r(" 当前灰度: ",-1)),n("strong",Rt,f(k.percentage)+"%",1)])]),_:1}),l(D,{title:nl(),type:"info",closable:!1,style:{"margin-bottom":"20px"}},{default:t(()=>[n("p",Dt,f(sl()),1)]),_:1},8,["title"]),l(d,{label:"补丁状态"},{default:t(()=>[l(ge,{modelValue:k.status,"onUpdate:modelValue":e[55]||(e[55]=a=>k.status=a)},{default:t(()=>[l(J,{label:"active"},{default:t(()=>[...e[146]||(e[146]=[r("启用",-1)])]),_:1}),l(J,{label:"inactive"},{default:t(()=>[...e[147]||(e[147]=[r("停用",-1)])]),_:1})]),_:1},8,["modelValue"])]),_:1}),l(d,{label:"强制更新"},{default:t(()=>[l(O,{modelValue:k.forceUpdate,"onUpdate:modelValue":e[56]||(e[56]=a=>k.forceUpdate=a)},null,8,["modelValue"]),e[148]||(e[148]=n("div",{style:{"font-size":"12px",color:"#999","margin-top":"4px"}}," 开启后用户必须更新才能使用应用 ",-1))]),_:1})]),_:1},8,["model"])]),_:1},8,["modelValue"])])):x("",!0)}}},Tt=kl(Ot,[["__scopeId","data-v-e3743d37"]]);export{Tt as default};
