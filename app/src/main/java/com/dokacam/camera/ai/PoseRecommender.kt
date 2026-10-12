package com.dokacam.camera.ai

import com.dokacam.camera.data.model.PoseTemplate
import com.dokacam.camera.data.model.PoseTemplates

/**
 * 场景 → 模板推荐：复用已检测到的 COCO 物体标签 + 光线 + 镜头朝向，
 * 零新增模型，给小白一条「现在该拍什么姿势」的捷径。
 *
 * 推荐语按「闺蜜给建议」的口吻写，而不是工程化的「检测到 chair」——
 * 目标用户是女生，建议要温柔、有画面感。
 */
object PoseRecommender {

    data class Rec(val template: PoseTemplate, val reason: String)

    /**
     * 返回 0~3 条推荐（去重、按场景相关度排序）。
     * @param objects    已检测到的 COCO 物体（带标签）
     * @param lowLight   帧亮度偏低（夜景/室内暖光）
     * @param frontFacing 当前是否前置镜头
     */
    fun recommend(objects: List<ObjectHit>, lowLight: Boolean, frontFacing: Boolean): List<Rec> {
        val recs = ArrayList<Rec>()
        val labels = objects.map { it.label }.toSet()

        if (labels.any { it in CHAIR }) {
            recs += Rec(PoseTemplates.byId.getValue("cafe_sit"), "有椅子，侧坐构图更出片")
        }
        if (labels.any { it in BENCH }) {
            recs += Rec(PoseTemplates.byId.getValue("stairs_sit"), "有长椅，坐下伸腿显腿长")
        }
        if (labels.any { it in COUCH }) {
            recs += Rec(PoseTemplates.byId.getValue("home_cozy"), "窝进沙发，暖光下更有氛围感")
        }
        if (lowLight) {
            recs += Rec(PoseTemplates.byId.getValue("night_portrait"), "光线偏暗，正适合夜景人像")
        }
        if (frontFacing) {
            recs += Rec(PoseTemplates.byId.getValue("mirror_selfie"), "前置镜头，对镜自拍最自然")
        }

        // 无明确场景信号时，给两条不挑场景的经典款兜底
        if (recs.isEmpty()) {
            recs += Rec(PoseTemplates.byId.getValue("stand_tall"), "经典站姿，拍出大长腿")
            recs += Rec(PoseTemplates.byId.getValue("look_back"), "回眸抓拍，自然又有故事感")
        }

        val seen = HashSet<String>()
        return recs.filter { seen.add(it.template.id) }.take(3)
    }

    private val CHAIR = setOf("chair")
    private val BENCH = setOf("bench")
    private val COUCH = setOf("couch", "sofa", "bed")
}
