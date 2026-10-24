// Publish the actual GSI reference KeyMint, explicitly SOFTWARE, on private Binder.
// No TEE, host Keystore, credential storage or attestation request is involved.
#include <android/binder_interface_utils.h>
#include <android/binder_parcel.h>
#include <dlfcn.h>
#include <cstdio>
#include <cstdint>
#include <memory>
namespace aidl::android::hardware::security::keymint {
enum class SecurityLevel : int32_t { SOFTWARE=0 };
class IKeyMintDevice;
}
using namespace aidl::android::hardware::security::keymint;
template<class T> static T symbol(void *library,const char *name){
    auto value=reinterpret_cast<T>(dlsym(library,name));
    if(!value){fprintf(stderr,"SOFT_KEYMINT_SYMBOL_MISSING=%s %s\n",name,dlerror());_Exit(2);}return value;
}
int main(){
    setbuf(stdout,nullptr);
    void *implementation=dlopen("libkeymint_fake_latest.so",RTLD_NOW|RTLD_LOCAL);
    if(!implementation){fprintf(stderr,"SOFT_KEYMINT_LOAD_ERROR=%s\n",dlerror());return 3;}
    using Factory=std::shared_ptr<IKeyMintDevice>(*)(SecurityLevel);
    auto create=symbol<Factory>(implementation,"_ZN4aidl7android8hardware8security7keymint19CreateKeyMintDeviceENS3_13SecurityLevelE");
    auto device=create(SecurityLevel::SOFTWARE);if(!device)return 4;
    // IKeyMintDevice directly derives from the stable NDK ICInterface base (offset 0).
    // Keep the factory's real shared ownership, without reproducing generated AIDL classes.
    std::shared_ptr<ndk::ICInterface> interface(device,reinterpret_cast<ndk::ICInterface*>(device.get()));
    auto binder=interface->asBinder();if(!binder.get())return 5;
    AParcel *input=nullptr,*output=nullptr;AStatus *status=nullptr;
    binder_status_t result=AIBinder_prepareTransaction(binder.get(),&input);
    if(result==STATUS_OK)result=AIBinder_transact(binder.get(),1,&input,&output,0);
    int32_t present=0,size=0,version=0,security=-1;
    if(result==STATUS_OK)result=AParcel_readStatusHeader(output,&status);
    if(result==STATUS_OK&&status&&AStatus_isOk(status))result=AParcel_readInt32(output,&present);
    else result=STATUS_BAD_VALUE;
    if(result==STATUS_OK)result=AParcel_readInt32(output,&size);
    if(result==STATUS_OK)result=AParcel_readInt32(output,&version);
    if(result==STATUS_OK)result=AParcel_readInt32(output,&security);
    if(status)AStatus_delete(status);if(output)AParcel_delete(output);
    if(result!=STATUS_OK||present!=1||size<12||version<=0||security!=0){fprintf(stderr,"SOFT_KEYMINT_HARDWARE_INFO_REJECTED status=%d present=%d size=%d version=%d security=%d\n",result,present,size,version,security);return 6;}
    printf("PRIVATE_KEYMINT_ACTUAL_HARDWARE_INFO_VERSION=%d_SECURITY_LEVEL=SOFTWARE\n",version);
    void *ndk=dlopen("libbinder_ndk.so",RTLD_NOW|RTLD_LOCAL);if(!ndk)return 7;
    auto set_threads=symbol<void(*)(uint32_t)>(ndk,"ABinderProcess_setThreadPoolMaxThreadCount");
    auto start_threads=symbol<void(*)()>(ndk,"ABinderProcess_startThreadPool");
    auto add=symbol<binder_status_t(*)(AIBinder*,const char*)>(ndk,"AServiceManager_addService");
    auto join=symbol<void(*)()>(ndk,"ABinderProcess_joinThreadPool");
    set_threads(4);start_threads();
    result=add(binder.get(),"android.hardware.security.keymint.IKeyMintDevice/default");
    if(result!=STATUS_OK){fprintf(stderr,"SOFT_KEYMINT_REGISTER_ERROR=%d\n",result);return 8;}
    puts("PRIVATE_SOFTWARE_KEYMINT_REGISTERED_NO_HARDWARE_SECURITY");join();return 0;
}
