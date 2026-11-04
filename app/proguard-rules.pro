# kotlinx.serialization: keep the generated serializers of the request/response classes.
-keepattributes *Annotation*, InnerClasses
-keepclassmembers @kotlinx.serialization.Serializable class app.binelik.** { *** Companion; *** serializer(...); }
-keep,includedescriptorclasses class app.binelik.**$$serializer { *; }
