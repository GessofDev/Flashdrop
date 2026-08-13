rootProject.name = "flashdrop-services"

include("shared-observability")
include("auth-service")
include("catalog-service")
include("delivery-service")
// orders-service es Maven (no se incluye acá, se construye por separado)
