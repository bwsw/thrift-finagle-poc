include "struct.thrift"

#@namespace scala  com.bwsw.thriftFinaglePoC.service


exception ServiceException {
  1: string reason
}


service SampleService {

   void ping(),

   i32 add(1: i32 num1, 2: i32 num2) throws (1: ServiceException serviceEx),

   list <i32> inc(1: list <i32> nums, 2: i32 inc),

   struct.SampleStruct createStruct(1: i32 key, 2: string value)

}
