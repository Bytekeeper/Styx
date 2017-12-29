package org.fttbot.import
import bwapi.*
class FBulletType(val source : BulletType, val name : String) {
   init {
      FBulletType.instances[source] = this
   }
   override fun toString() = name

   companion object {
      internal val instances = HashMap<BulletType, FBulletType>()
      fun of(src : BulletType) : FBulletType? = instances[src]
      val Melee : FBulletType = FBulletType(source = BulletType.Melee, name = "Melee")
      val Fusion_Cutter_Hit : FBulletType = FBulletType(source = BulletType.Fusion_Cutter_Hit, name = "Fusion_Cutter_Hit")
      val Gauss_Rifle_Hit : FBulletType = FBulletType(source = BulletType.Gauss_Rifle_Hit, name = "Gauss_Rifle_Hit")
      val C_10_Canister_Rifle_Hit : FBulletType = FBulletType(source = BulletType.C_10_Canister_Rifle_Hit, name = "C_10_Canister_Rifle_Hit")
      val Gemini_Missiles : FBulletType = FBulletType(source = BulletType.Gemini_Missiles, name = "Gemini_Missiles")
      val Fragmentation_Grenade : FBulletType = FBulletType(source = BulletType.Fragmentation_Grenade, name = "Fragmentation_Grenade")
      val Longbolt_Missile : FBulletType = FBulletType(source = BulletType.Longbolt_Missile, name = "Longbolt_Missile")
      val ATS_ATA_Laser_Battery : FBulletType = FBulletType(source = BulletType.ATS_ATA_Laser_Battery, name = "ATS_ATA_Laser_Battery")
      val Burst_Lasers : FBulletType = FBulletType(source = BulletType.Burst_Lasers, name = "Burst_Lasers")
      val Arclite_Shock_Cannon_Hit : FBulletType = FBulletType(source = BulletType.Arclite_Shock_Cannon_Hit, name = "Arclite_Shock_Cannon_Hit")
      val EMP_Missile : FBulletType = FBulletType(source = BulletType.EMP_Missile, name = "EMP_Missile")
      val Dual_Photon_Blasters_Hit : FBulletType = FBulletType(source = BulletType.Dual_Photon_Blasters_Hit, name = "Dual_Photon_Blasters_Hit")
      val Particle_Beam_Hit : FBulletType = FBulletType(source = BulletType.Particle_Beam_Hit, name = "Particle_Beam_Hit")
      val Anti_Matter_Missile : FBulletType = FBulletType(source = BulletType.Anti_Matter_Missile, name = "Anti_Matter_Missile")
      val Pulse_Cannon : FBulletType = FBulletType(source = BulletType.Pulse_Cannon, name = "Pulse_Cannon")
      val Psionic_Shockwave_Hit : FBulletType = FBulletType(source = BulletType.Psionic_Shockwave_Hit, name = "Psionic_Shockwave_Hit")
      val Psionic_Storm : FBulletType = FBulletType(source = BulletType.Psionic_Storm, name = "Psionic_Storm")
      val Yamato_Gun : FBulletType = FBulletType(source = BulletType.Yamato_Gun, name = "Yamato_Gun")
      val Phase_Disruptor : FBulletType = FBulletType(source = BulletType.Phase_Disruptor, name = "Phase_Disruptor")
      val STA_STS_Cannon_Overlay : FBulletType = FBulletType(source = BulletType.STA_STS_Cannon_Overlay, name = "STA_STS_Cannon_Overlay")
      val Sunken_Colony_Tentacle : FBulletType = FBulletType(source = BulletType.Sunken_Colony_Tentacle, name = "Sunken_Colony_Tentacle")
      val Acid_Spore : FBulletType = FBulletType(source = BulletType.Acid_Spore, name = "Acid_Spore")
      val Glave_Wurm : FBulletType = FBulletType(source = BulletType.Glave_Wurm, name = "Glave_Wurm")
      val Seeker_Spores : FBulletType = FBulletType(source = BulletType.Seeker_Spores, name = "Seeker_Spores")
      val Queen_Spell_Carrier : FBulletType = FBulletType(source = BulletType.Queen_Spell_Carrier, name = "Queen_Spell_Carrier")
      val Plague_Cloud : FBulletType = FBulletType(source = BulletType.Plague_Cloud, name = "Plague_Cloud")
      val Consume : FBulletType = FBulletType(source = BulletType.Consume, name = "Consume")
      val Ensnare : FBulletType = FBulletType(source = BulletType.Ensnare, name = "Ensnare")
      val Needle_Spine_Hit : FBulletType = FBulletType(source = BulletType.Needle_Spine_Hit, name = "Needle_Spine_Hit")
      val Invisible : FBulletType = FBulletType(source = BulletType.Invisible, name = "Invisible")
      val Optical_Flare_Grenade : FBulletType = FBulletType(source = BulletType.Optical_Flare_Grenade, name = "Optical_Flare_Grenade")
      val Halo_Rockets : FBulletType = FBulletType(source = BulletType.Halo_Rockets, name = "Halo_Rockets")
      val Subterranean_Spines : FBulletType = FBulletType(source = BulletType.Subterranean_Spines, name = "Subterranean_Spines")
      val Corrosive_Acid_Shot : FBulletType = FBulletType(source = BulletType.Corrosive_Acid_Shot, name = "Corrosive_Acid_Shot")
      val Neutron_Flare : FBulletType = FBulletType(source = BulletType.Neutron_Flare, name = "Neutron_Flare")
      val None : FBulletType = FBulletType(source = BulletType.None, name = "None")
      val Unknown : FBulletType = FBulletType(source = BulletType.Unknown, name = "Unknown")
   }
}
