(ns infra.render.material-surface
  "Read-only smooth scalar-surface pass inside the existing Truth renderer."
  (:require [infra.render.shader :as shader] [infra.render.passes :as passes]
            [infra.render.math :as rmath])
  (:import (org.lwjgl.opengl GL11 GL15 GL20 GL30)
           (org.lwjgl BufferUtils) (java.nio ByteOrder)
           (java.nio.file Files Paths)))

(def ^:private program
  {:name :material-surface :version "330 core"
   :vertex {:inputs {:aPos :vec3 :aNormal :vec3 :aColor :vec3}
            :uniforms {:model :mat4 :projection :mat4 :view :mat4}
            :outputs {:vNormal :vec3 :vColor :vec3 :vPos :vec3}
            :source "#version 330 core
layout(location=0) in vec3 aPos;
layout(location=1) in vec3 aNormal;
layout(location=2) in vec3 aColor;
uniform mat4 model, projection, view;
out vec3 vNormal, vColor, vPos;
void main(){vec4 p=model*vec4(aPos,1);vPos=p.xyz;vNormal=aNormal;vColor=aColor;gl_Position=projection*view*p;}"}
   :fragment {:inputs {:vNormal :vec3 :vColor :vec3 :vPos :vec3}
              :uniforms {:cameraPos :vec3} :outputs {:FragColor :vec4}
              :source "#version 330 core
in vec3 vNormal, vColor, vPos;
uniform vec3 cameraPos;
out vec4 FragColor;
void main(){
vec3 n=normalize(vNormal);vec3 v=normalize(cameraPos-vPos);
vec3 light=normalize(vec3(-0.55,-0.7,1.0));
float diffuse=max(dot(n,light),0.0);
float rim=pow(1.0-max(dot(n,v),0.0),3.0);
vec3 c=vColor*(0.26+0.74*diffuse)+vec3(0.10,0.15,0.20)*rim;
FragColor=vec4(pow(c,vec3(0.85)),1.0);
}"}})

(defonce ^:private cached-mesh (atom nil))

(defn- mesh! [path]
  (if (= path (:path @cached-mesh)) @cached-mesh
    (let [_ (when-let [old @cached-mesh]
              (GL15/glDeleteBuffers (int (:vbo old)))
              (GL30/glDeleteVertexArrays (int (:vao old))))
          bytes (Files/readAllBytes (Paths/get path (make-array String 0)))
          _ (when-not (zero? (mod (alength bytes) 36))
              (throw (ex-info "Malformed surface mesh" {:path path})))
          buf (doto (BufferUtils/createByteBuffer (alength bytes))
                (.order (ByteOrder/LITTLE_ENDIAN)) (.put bytes) (.flip))
          vao (GL30/glGenVertexArrays) vbo (GL15/glGenBuffers)]
      (GL30/glBindVertexArray vao)
      (GL15/glBindBuffer GL15/GL_ARRAY_BUFFER vbo)
      (GL15/glBufferData GL15/GL_ARRAY_BUFFER buf GL15/GL_STATIC_DRAW)
      (doseq [location (range 3)]
        (GL20/glVertexAttribPointer (int location) 3 GL11/GL_FLOAT false 36 (long (* location 12)))
        (GL20/glEnableVertexAttribArray (int location)))
      (let [result {:path path :vao vao :vbo vbo :count (quot (alength bytes) 36)}]
        (reset! cached-mesh result) result))))

(defn draw!
  "Draw extracted surfaces with actual gradient normals and interpolated composition."
  [projection view camera surfaces]
  (when (seq surfaces)
    (let [pid (:id (shader/compile-program! program))]
      (passes/set-blend! :none) (passes/set-depth-write! true)
      (GL20/glUseProgram (int pid))
      (doseq [shape surfaces]
        (let [mesh (mesh! (:surface-file shape))]
          (passes/bind-uniforms! pid {:projection projection :view view
                                     :model (rmath/model-matrix (:position shape) 1.0)
                                     :cameraPos (:position camera)})
          (GL30/glBindVertexArray (int (:vao mesh)))
          (GL11/glDrawArrays GL11/GL_TRIANGLES 0 (int (:count mesh)))))
      (GL30/glBindVertexArray 0))))
