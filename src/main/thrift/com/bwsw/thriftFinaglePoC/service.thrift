include "struct.thrift"

#@namespace scala  com.bwsw.thriftFinaglePoC.service


exception ServiceException {
  1: string reason
}


service SampleService {

   i32 add(1: i32 num1, 2: i32 num2) throws (1: ServiceException serviceEx),

   struct.SampleStruct createStruct(1: i32 key, 2: string value)

}
