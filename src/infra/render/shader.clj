(ns infra.render.shader
  "Shader program records, compile/link, and a source-hash keyed program cache.

   Programs are declared as data with :name, :version, :vertex and :fragment
   stages. Each stage lists its :inputs, :uniforms, :outputs and the raw GLSL
   :source string. This gives us named slots for validation and hot-reload
   without taking on a Clojure-to-GLSL compiler dependency."
  (:require
    [clojure.string :as str]
    [law.render :as law])
  (:import
    (org.lwjgl.opengl GL20)))

;; ---------------------------------------------------------------------------
;; Program cache
;; ---------------------------------------------------------------------------

(def ^:private program-cache-atom
  "name -> {id program-id hash source-hash}. Invalidated by source changes."
  (atom {}))

(def program-cache program-cache-atom)

(defn source-hash
  "Stable hash of the combined vertex + fragment source for cache invalidation."
  [{:keys [version vertex fragment]}]
  (hash [version (:source vertex) (:source fragment)]))

(defn- compile-shader
  "Compile a single shader. Throws ex-info with :log on failure."
  [source typ]
  (let [id (GL20/glCreateShader typ)]
    (GL20/glShaderSource id source)
    (GL20/glCompileShader id)
    (when (zero? (GL20/glGetShaderi id GL20/GL_COMPILE_STATUS))
      (let [log (GL20/glGetShaderInfoLog id)]
        (GL20/glDeleteShader id)
        (throw (ex-info "Shader compile failed"
                        {:type typ :log log :source source}))))
    id))

(defn- link-program
  "Link vertex and fragment shader ids into a program, deleting the shaders
   after a successful link. Throws ex-info with :log on failure."
  [vs-id fs-id]
  (let [program (GL20/glCreateProgram)]
    (GL20/glAttachShader program vs-id)
    (GL20/glAttachShader program fs-id)
    (GL20/glLinkProgram program)
    (let [ok (pos? (GL20/glGetProgrami program GL20/GL_LINK_STATUS))]
      (GL20/glDeleteShader vs-id)
      (GL20/glDeleteShader fs-id)
      (when-not ok
        (let [log (GL20/glGetProgramInfoLog program)]
          (GL20/glDeleteProgram program)
          (throw (ex-info "Program link failed" {:log log}))))
      program)))

(defn compile-program
  "Compile a `ProgramDef` map into an OpenGL program id. Validates the shape
   with `law.render` before touching GL. Prints a short message to stdout so
   REPL hot-reloads are visible."
  [program-def]
  (when-not (law/valid-program-def? program-def)
    (throw (ex-info "Invalid shader program definition"
                    {:program program-def
                     :explain (malli.core/explain law/program-def program-def)})))
  (println (str "Compiling " (name (:name program-def)) " shaders..."))
  (let [vs (compile-shader (:source (:vertex program-def)) GL20/GL_VERTEX_SHADER)
        fs (compile-shader (:source (:fragment program-def)) GL20/GL_FRAGMENT_SHADER)]
    (link-program vs fs)))

(defn compile-program!
  "Compile `program-def` and cache it by name + source hash. Returns the
   cached entry `{id program-id hash source-hash}`."
  [program-def]
  (let [name  (:name program-def)
        h     (source-hash program-def)
        cache @program-cache]
    (if-let [entry (get cache name)]
      (if (= h (:hash entry))
        entry
        (let [id (compile-program program-def)]
          (swap! program-cache assoc name {:id id :hash h})
          {:id id :hash h}))
      (let [id (compile-program program-def)]
        (swap! program-cache assoc name {:id id :hash h})
        {:id id :hash h}))))

(defn program-id
  "Return the cached GL program id for `name`, or nil if not compiled."
  [name]
  (some-> (get @program-cache name) :id))

(defn program-hash
  "Return the cached source hash for `name`, or nil."
  [name]
  (some-> (get @program-cache name) :hash))

(defn invalidate-program!
  "Delete and remove a cached program. Safe if it does not exist."
  [name]
  (when-let [id (program-id name)]
    (GL20/glDeleteProgram id))
  (swap! program-cache dissoc name))

(defn invalidate-all!
  "Delete every cached program and clear the cache."
  []
  (doseq [[name entry] @program-cache]
    (GL20/glDeleteProgram (:id entry)))
  (reset! program-cache {}))

;; ---------------------------------------------------------------------------
;; Built-in program definitions
;; ---------------------------------------------------------------------------

(def body-program
  "Shaded sphere with view-dependent diffuse + fresnel glow."
  {:name :body
   :version "330 core"
   :vertex {:inputs    {:aPos :vec3}
            :uniforms  {:model :mat4 :view :mat4 :projection :mat4}
            :outputs   {:vNormal :vec3 :vWorldPos :vec3}
            :source    "#version 330 core
                        layout(location = 0) in vec3 aPos;
                        out vec3 vNormal;
                        out vec3 vWorldPos;
                        uniform mat4 model;
                        uniform mat4 view;
                        uniform mat4 projection;
                        void main() {
                          vNormal = mat3(transpose(inverse(model))) * aPos;
                          vec4 worldPos = model * vec4(aPos, 1.0);
                          vWorldPos = worldPos.xyz;
                          gl_Position = projection * view * worldPos;
                        }"}
   :fragment {:inputs    {:vNormal :vec3 :vWorldPos :vec3}
              :uniforms  {:color :vec3 :cameraPos :vec3 :glow :float}
              :outputs   {:FragColor :vec4}
              :source    "#version 330 core
                          in vec3 vNormal;
                          in vec3 vWorldPos;
                          out vec4 FragColor;
                          uniform vec3 color;
                          uniform vec3 cameraPos;
                          uniform float glow;
                          void main() {
                            vec3 N = normalize(vNormal);
                            vec3 V = normalize(cameraPos - vWorldPos);
                            float diff = max(dot(N, V), 0.0);
                            float fresnel = pow(1.0 - abs(dot(N, V)), 2.0);
                            vec3 surface = color * (0.15 + 0.35 * diff);
                            vec3 glowColor = color * glow * (0.8 + 0.6 * fresnel);
                            FragColor = vec4(surface + glowColor, 1.0);
                          }"}})

(def particle-program
  "Soft, density-modulated nebula fog particle."
  {:name :particle
   :version "330 core"
   :vertex {:inputs    {:aPos :vec3 :aColor :vec3 :aSize :float :aDensity :float}
            :uniforms  {:view :mat4 :projection :mat4 :cameraPos :vec3}
            :outputs   {:vColor :vec3 :vDensity :float}
            :source    "#version 330 core
                        layout(location = 0) in vec3 aPos;
                        layout(location = 1) in vec3 aColor;
                        layout(location = 2) in float aSize;
                        layout(location = 3) in float aDensity;
                        out vec3 vColor;
                        out float vDensity;
                        uniform mat4 view;
                        uniform mat4 projection;
                        uniform vec3 cameraPos;
                        void main() {
                          vColor = aColor;
                          vDensity = aDensity;
                          gl_Position = projection * view * vec4(aPos, 1.0);
                          float dist = length(cameraPos - aPos);
                          gl_PointSize = clamp(aSize / (1.0 + dist * 0.005), 2.0, 200.0);
                        }"}
   :fragment {:inputs    {:vColor :vec3 :vDensity :float}
              :uniforms  {:time :float}
              :outputs   {:FragColor :vec4}
              :source    "#version 330 core
                          in vec3 vColor;
                          in float vDensity;
                          out vec4 FragColor;
                          uniform float time;
                          float hash(vec2 p) {
                            return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453);
                          }
                          float noise(vec2 p) {
                            vec2 i = floor(p);
                            vec2 f = fract(p);
                            f = f * f * (3.0 - 2.0 * f);
                            float a = hash(i);
                            float b = hash(i + vec2(1.0, 0.0));
                            float c = hash(i + vec2(0.0, 1.0));
                            float d = hash(i + vec2(1.0, 1.0));
                            return mix(mix(a, b, f.x), mix(c, d, f.x), f.y);
                          }
                          void main() {
                            vec2 coord = gl_PointCoord - vec2(0.5);
                            float dist = length(coord);
                            float dens = clamp(vDensity, 0.0, 1.0);
                            float sigma = mix(0.32, 0.12, dens);
                            float alpha = exp(-(dist * dist) / (2.0 * sigma * sigma));
                            float n = noise(coord * 8.0 + time * 0.3);
                            alpha *= (0.04 + 1.8 * pow(dens, 1.5)) * (0.35 + 0.65 * n);
                            if (alpha < 0.003) discard;
                            vec3 color = mix(vColor * 0.55, vec3(1.0), 0.28 * dens);
                            FragColor = vec4(color * alpha, alpha);
                          }"}})

(def sprite-program
  "Screen-space point sprite for distant body LOD proxies."
  {:name :sprite
   :version "330 core"
   :vertex {:inputs    {:aPos :vec3 :aColor :vec3 :aSize :float}
            :uniforms  {:view :mat4 :projection :mat4}
            :outputs   {:vColor :vec3}
            :source    "#version 330 core
                        layout(location = 0) in vec3 aPos;
                        layout(location = 1) in vec3 aColor;
                        layout(location = 2) in float aSize;
                        out vec3 vColor;
                        uniform mat4 view;
                        uniform mat4 projection;
                        void main() {
                          vColor = aColor;
                          gl_Position = projection * view * vec4(aPos, 1.0);
                          gl_PointSize = aSize;
                        }"}
   :fragment {:inputs    {:vColor :vec3}
              :uniforms  {}
              :outputs   {:FragColor :vec4}
              :source    "#version 330 core
                          in vec3 vColor;
                          out vec4 FragColor;
                          void main() {
                            vec2 coord = gl_PointCoord - vec2(0.5);
                            float r = length(coord);
                            if (r > 0.5) discard;
                            float alpha = 1.0 - smoothstep(0.25, 0.5, r);
                            FragColor = vec4(vColor * alpha, alpha);
                          }"}})

(def line-program
  "Pass-through line shader for magnetic field lines."
  {:name :line
   :version "330 core"
   :vertex {:inputs    {:aPos :vec3 :aColor :vec3}
            :uniforms  {:view :mat4 :projection :mat4}
            :outputs   {:vColor :vec3}
            :source    "#version 330 core
                        layout(location = 0) in vec3 aPos;
                        layout(location = 1) in vec3 aColor;
                        out vec3 vColor;
                        uniform mat4 view;
                        uniform mat4 projection;
                        void main() {
                          vColor = aColor;
                          gl_Position = projection * view * vec4(aPos, 1.0);
                        }"}
   :fragment {:inputs    {:vColor :vec3}
              :uniforms  {}
              :outputs   {:FragColor :vec4}
              :source    "#version 330 core
                          in vec3 vColor;
                          out vec4 FragColor;
                          void main() { FragColor = vec4(vColor, 0.85); }"}})

(def hud-program
  "Solid-colour 2D HUD rectangle / text shader."
  {:name :hud
   :version "330 core"
   :vertex {:inputs    {:aPos :vec2}
            :uniforms  {}
            :outputs   {}
            :source    "#version 330 core
                        layout(location = 0) in vec2 aPos;
                        void main() { gl_Position = vec4(aPos, 0.0, 1.0); }"}
   :fragment {:inputs    {}
              :uniforms  {:hudColor :vec4}
              :outputs   {:FragColor :vec4}
              :source    "#version 330 core
                          out vec4 FragColor;
                          uniform vec4 hudColor;
                          void main() { FragColor = hudColor; }"}})

(def volume-program
  "Ray-marched volumetric fog fullscreen pass."
  {:name :volume
   :version "330 core"
   :vertex {:inputs    {:aPos :vec2}
            :uniforms  {}
            :outputs   {:vNdc :vec2}
            :source    "#version 330 core
                        layout(location=0) in vec2 aPos;
                        out vec2 vNdc;
                        void main(){ vNdc = aPos; gl_Position = vec4(aPos, 0.0, 1.0); }"}
   :fragment {:inputs    {:vNdc :vec2}
              :uniforms  {:camPos :vec3 :camRight :vec3 :camUp :vec3 :camFwd :vec3
                          :tanHalfFov :float :aspect :float
                          :boxMin :vec3 :boxMax :vec3
                          :volume :sampler3D
                          :kappa :float :emissionScale :float :scatterScale :float
                          :jitter :float :numLights :int
                          :lightPos [:vec3 8] :lightColor [:vec3 8] :lightIntensity [:float 8]}
              :outputs   {:FragColor :vec4}
              :source    "#version 330 core
                          in vec2 vNdc;
                          out vec4 FragColor;
                          uniform vec3  camPos, camRight, camUp, camFwd;
                          uniform float tanHalfFov, aspect;
                          uniform vec3  boxMin, boxMax;
                          uniform sampler3D volume;
                          uniform float kappa;
                          uniform float emissionScale;
                          uniform float scatterScale;
                          uniform float jitter;
                          uniform int   numLights;
                          uniform vec3  lightPos[8];
                          uniform vec3  lightColor[8];
                          uniform float lightIntensity[8];
                          float hash(vec2 p){ return fract(sin(dot(p, vec2(12.9898,78.233)))*43758.5453); }
                          bool intersectBox(vec3 ro, vec3 rd, out float t0, out float t1){
                            vec3 inv = 1.0/rd;
                            vec3 a = (boxMin-ro)*inv, b = (boxMax-ro)*inv;
                            vec3 tmn = min(a,b), tmx = max(a,b);
                            t0 = max(max(tmn.x,tmn.y),tmn.z);
                            t1 = min(min(tmx.x,tmx.y),tmx.z);
                            return t1 > max(t0,0.0);
                          }
                          vec3 uvw(vec3 p){ return (p-boxMin)/(boxMax-boxMin); }
                          void main(){
                            vec3 ro = camPos;
                            vec3 rd = normalize(camFwd
                                        + vNdc.x*aspect*tanHalfFov*camRight
                                        + vNdc.y*tanHalfFov*camUp);
                            float t0,t1;
                            if(!intersectBox(ro,rd,t0,t1)){ discard; }
                            t0 = max(t0,0.0);
                            float span = t1 - t0;
                            int steps = clamp(int(span/(length(boxMax-boxMin)/96.0)), 16, 192);
                            float dt = span/float(steps);
                            float j = jitter*hash(gl_FragCoord.xy);
                            float T = 1.0;
                            vec3  C = vec3(0.0);
                            for(int i=0;i<steps;i++){
                              float t = t0 + (float(i)+j)*dt;
                              vec3 p = ro + rd*t;
                              vec4 s = texture(volume, uvw(p));
                              float dens = s.a;
                              if(dens > 0.0008){
                                float sigma = kappa*dens;
                                float a = 1.0 - exp(-sigma*dt);
                                vec3 scat = vec3(0.0);
                                for(int l=0;l<numLights;l++){
                                  vec3 d = lightPos[l]-p;
                                  float r2 = dot(d,d)+0.02;
                                  float Ts = 1.0;
                                  vec3 ld = d/sqrt(r2);
                                  float sdt = length(boxMax-boxMin)/28.0;
                                  for(int k=0;k<5;k++){
                                    vec3 sp = p + ld*(float(k)+0.5)*sdt;
                                    Ts *= exp(-kappa*texture(volume, uvw(sp)).a*sdt);
                                  }
                                  scat += lightColor[l]*(lightIntensity[l]/r2)*Ts;
                                }
                                vec3 L = emissionScale*s.rgb + scatterScale*dens*scat;
                                C += T * a * L;
                                T *= (1.0-a);
                                if(T < 0.01) break;
                              }
                            }
                            FragColor = vec4(C, 1.0-T);
                          }"}})

(def builtin-programs
  "All built-in shader program definitions."
  [body-program particle-program sprite-program line-program hud-program volume-program])

(defn ensure-builtins!
  "Compile every built-in program and return a map of program name -> GL id."
  []
  (into {} (map (fn [p] [(:name p) (:id (compile-program! p))])) builtin-programs))
