package com.dokacam.camera.ai

import com.dokacam.camera.data.model.PoseCategory
import com.dokacam.camera.data.model.PoseTemplate
import com.dokacam.camera.data.model.PoseTemplates

/**
 * 自拍姿势推荐：按「取景距离」（主脸大小）+ 镜头朝向 + 光线给建议，
 * 完全不依赖场景物体 —— 白墙、对镜、空地都能出片。
 *
 * 推荐语按「闺蜜给建议」的口吻写，温柔、有画面感，而不是工程化描述。
 */
object PoseRecommender {

    data class Rec(val template: PoseTemplate, val reason: String)

    /** 主脸宽度占视图宽的比例推断取景：null（没检测到脸）→ 全身 */
    fun inferCategory(faceSizeFrac: Float?): PoseCategory = when {
        faceSizeFrac == null -> PoseCategory.FULL
        faceSizeFrac > 0.22f -> PoseCategory.HEAD
        faceSizeFrac > 0.10f -> PoseCategory.HALF
        else -> PoseCategory.FULL
    }

    /**
     * 返回 0~3 条推荐（去重、按取景相关度排序）。
     * @param faceSizeFrac 主脸宽/视图宽（null = 未检测到脸）
     * @param frontFacing  当前是否前置镜头
     * @param lowLight     帧亮度偏低（夜景/室内暗光）
     */
    fun recommend(faceSizeFrac: Float?, frontFacing: Boolean, lowLight: Boolean): List<Rec> {
        val recs = ArrayList<Rec>()
        when (inferCategory(faceSizeFrac)) {
            PoseCategory.HEAD -> {
                if (frontFacing) {
                    recs += Rec(PoseTemplates.byId.getValue("head_pout"), "前置自拍，试试松弛感大头照")
                    recs += Rec(PoseTemplates.byId.getValue("head_hair"), "撩发瞬间最出片")
                } else {
                    recs += Rec(PoseTemplates.byId.getValue("head_chin"), "大头照托个脸，脸小一圈")
                }
            }
            PoseCategory.HALF -> {
                recs += Rec(PoseTemplates.byId.getValue("half_collarbone"), "半身构图，手放锁骨显气质")
                if (frontFacing) {
                    recs += Rec(PoseTemplates.byId.getValue("half_prop"), "拿个小道具，手上不空更好看")
                }
            }
            PoseCategory.FULL -> {
                if (frontFacing) {
                    recs += Rec(PoseTemplates.byId.getValue("full_mirror"), "前置镜头，对镜自拍最自然")
                } else {
                    recs += Rec(PoseTemplates.byId.getValue("full_sline"), "退远一点，全身 S 站姿显腿长")
                    recs += Rec(PoseTemplates.byId.getValue("full_crossleg"), "交叉腿一站，比例立刻拉长")
                }
            }
        }
        if (lowLight) {
            recs += Rec(PoseTemplates.byId.getValue("head_side"), "光线偏暗，侧脸吃光更有轮廓")
        }

        // 兜底：两条不挑景的经典款
        if (recs.isEmpty()) {
            recs += Rec(PoseTemplates.byId.getValue("full_sline"), "经典 S 站姿，拍出大长腿")
            recs += Rec(PoseTemplates.byId.getValue("half_lookback"), "回眸抓拍，自然又有故事感")
        }

        val seen = HashSet<String>()
        return recs.filter { seen.add(it.template.id) }.take(3)
    }
}
