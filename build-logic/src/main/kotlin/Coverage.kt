import kotlinx.kover.gradle.plugin.dsl.KoverProjectExtension
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

internal const val COVERAGE_VARIANT = "coverage"

internal fun Project.configureCoverage(vararg variants: String) {
    extensions.configure<KoverProjectExtension> {
        currentProject {
            createVariant(COVERAGE_VARIANT) {
                add(*variants, optional = true)
            }
        }
        reports {
            total {
                verify {
                    onCheck.set(false)
                }
            }
        }
    }
}
